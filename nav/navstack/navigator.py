"""Navigator: camera -> navserve (:next) -> R2D2 motion (:move), with failsafes.

One asyncio loop, three cooperating tasks (plan.md §5):
  * sampler   -- grab JPEG at --sample-fps, send {"action":"next", instruction}
                 to navserve; at most ONE request in flight (like robot_deploy's
                 vln_client: rate = 1/latency, never a backlog);
  * driver    -- 3 Hz deadman: latest waypoint chunk -> (power, angle); on
                 staleness (> --stale-after), backend error, stop, or SIGINT
                 -> move(0,0);
  * lease     -- refresh user_control every 5 s (12 s robot timeout).

Safety: the driver ALWAYS keeps sending move(0,0) until released -- the MCU has
no watchdog of its own for our client class, and r2d2's own 12 s lease handles
a crashed navigator by reverting to READY (which stops USER_CONTROL but does
not send a final stop, so a clean exit must).
"""

from __future__ import annotations

import asyncio
import base64
import json
import logging
import time
from typing import Optional

import numpy as np
from websockets.asyncio.client import connect

from .r2d2_link import R2D2Link
from .waypoints_to_cmd import MotionParams, waypoint_to_motion

logger = logging.getLogger("navstack.navigator")


class Navigator:
    def __init__(
        self,
        server_url: str,            # ws://host:8050
        robot_host: str,            # r2d2 host for :8887
        instruction: str,
        frame_source,               # object with connect()/get_jpeg()/close()
        sample_fps: float = 4.0,
        control_hz: float = 3.0,
        stale_after_s: float = 4.0,
        max_wp_age_s: float = 2.5,
        motion: Optional[MotionParams] = None,
        dry_run: bool = False,      # log commands, do not touch the robot
        goal_image: str = "",       # vint backend: goal photo (b64 via setGoal)
        goal_topomap: str = "",     # vint backend: node dir for graph navigation
        show: bool = False,         # live OpenCV window: camera + inference overlay
    ):
        self.server_url = server_url
        self.instruction = instruction
        self.goal_image = goal_image
        self.goal_topomap = goal_topomap
        self.source = frame_source
        self.sample_dt = 1.0 / max(sample_fps, 0.1)
        self.control_dt = 1.0 / control_hz
        self.stale_after = stale_after_s
        self.max_wp_age = max_wp_age_s
        self.motion = motion or MotionParams()
        self.dry_run = dry_run

        self.robot = R2D2Link(robot_host) if not dry_run else None
        self.waypoints: Optional[np.ndarray] = None
        self.wp_time = 0.0
        self.stop_flag = False
        self.last_error: Optional[str] = None
        self._seq = 0
        self._inflight = False
        self._tasks: list[asyncio.Task] = []
        self.show = show
        self._viewer = None
        self._latest_jpeg: Optional[bytes] = None      # last frame from source
        self._last_cmd: tuple[int, int] = (0, 0)       # last motion command
        self._last_latency: Optional[float] = None
        self._info: dict = {}                          # last server response fields
        if show:
            from .viewer import FrameViewer
            self._viewer = FrameViewer(title=f"navstack {robot_host}")

    # ------------------------------------------------------------- navserve
    async def _send_goal(self, ws) -> None:
        """vint backend: pin the goal (photo or topomap) right after login."""
        data = {}
        if self.goal_image:
            data["image"] = base64.b64encode(open(self.goal_image, "rb").read()).decode()
        elif self.goal_topomap:
            data["topomap"] = self.goal_topomap
        if not data:
            return
        await ws.send(json.dumps({"action": "setGoal", "data": data}))
        resp = json.loads(await ws.recv())
        logger.info("setGoal: %s", resp.get("data", {}).get("msg", resp))

    async def _frame_pump(self) -> None:
        """Sole reader of the camera source: keeps _latest_jpeg fresh even
        while navserve is down/reconnecting, so --show always has a frame."""
        while True:
            try:
                j = await self.source.get_jpeg()
                if j:
                    self._latest_jpeg = j
            except Exception as exc:
                logger.debug("frame pump: %s", exc)
            await asyncio.sleep(0.08)

    async def _next_frame(self, ws) -> None:
        jpeg = self._latest_jpeg
        if jpeg is None or self._inflight:
            return
        self._inflight = True
        self._seq += 1
        req = {"action": "next", "data": {
            "seq": self._seq,
            "image": base64.b64encode(jpeg).decode(),
            "instruction": self.instruction}}
        try:
            await ws.send(json.dumps(req))
            resp = json.loads(await asyncio.wait_for(ws.recv(), timeout=200.0))
            data = resp.get("data", {})
            if data.get("rc") != 0:
                self.last_error = f"server rc={data.get('rc')}: {data.get('msg')}"
                logger.warning("navserve error: %s", self.last_error)
                return
            actions = data.get("actions")
            if isinstance(actions, dict):
                actions = actions.get("actions")
            if actions:
                self.waypoints = np.asarray(actions, dtype=np.float32)
                self.wp_time = time.monotonic()
                self.last_error = None
            self.stop_flag = bool(data.get("stop"))
            self._info = {k: data.get(k) for k in
                          ("raw_text", "stop", "seq", "latency_ms",
                           "subgoal_node", "subgoal_dist_m") if k in data}
            if self.stop_flag:
                logger.info("model requested STOP (raw=%r)", data.get("raw_text"))
        except asyncio.TimeoutError:
            self.last_error = "navserve response timeout"
            logger.warning(self.last_error)
        finally:
            self._inflight = False

    async def _sampler(self) -> None:
        backoff = 1.0
        while True:
            try:
                async with connect(self.server_url, max_size=64 * 2**20) as ws:
                    await ws.send(json.dumps(
                        {"action": "login", "data": {"clientId": "r2d2-navigator"}}))
                    await ws.recv()
                    await self._send_goal(ws)
                    backoff = 1.0
                    logger.info("connected to navserve %s", self.server_url)
                    while True:
                        await self._next_frame(ws)
                        await asyncio.sleep(self.sample_dt)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                logger.warning("navserve connection lost: %s (retry in %.0fs)", exc, backoff)
                await asyncio.sleep(backoff)
                backoff = min(backoff * 2, 30.0)

    # ---------------------------------------------------------------- motion
    def _current_command(self) -> tuple[int, int]:
        if self.stop_flag:
            return 0, 0
        age = time.monotonic() - self.wp_time
        if self.waypoints is None or age > min(self.max_wp_age, self.stale_after):
            return 0, 0  # no fresh path: hold position
        try:
            return waypoint_to_motion(self.waypoints, self.motion)
        except ValueError as exc:
            logger.warning("waypoint decode failed: %s", exc)
            return 0, 0

    async def _driver(self) -> None:
        while True:
            power, angle = self._current_command()
            self._last_cmd = (power, angle)
            if self.dry_run:
                logger.info("[dry-run] move power=%d angle=%d (stop=%s err=%s)",
                            power, angle, self.stop_flag, self.last_error)
            else:
                assert self.robot is not None
                try:
                    await self.robot.move(power, angle)
                except Exception as exc:
                    logger.error("move send failed: %s", exc)
            self._render()
            await asyncio.sleep(self.control_dt)

    def _render(self) -> None:
        """Live camera + inference overlay (drive --show). Main-thread only."""
        if self._viewer is None or self._latest_jpeg is None:
            return
        info = dict(self._info)
        info["cmd"] = self._last_cmd
        if self.waypoints is not None:
            info["waypoints"] = self.waypoints.tolist()
        if self.last_error:
            info["raw_text"] = f"ERR {self.last_error}"
        self._viewer.render(self._latest_jpeg, info)

    async def _lease(self) -> None:
        while True:
            if self.robot is not None:
                try:
                    await self.robot.refresh_lease()
                except Exception as exc:
                    logger.error("lease refresh failed: %s", exc)
            await asyncio.sleep(min(5.0, self.control_dt))

    # ------------------------------------------------------------ lifecycle
    async def run(self) -> None:
        await self.source.connect()
        if self.robot is not None:
            await self.robot.connect()
            await self.robot.claim_control()
            logger.info("claimed user_control on %s", self.robot.url)
        self._tasks = [
            asyncio.create_task(self._frame_pump(), name="pump"),
            asyncio.create_task(self._sampler(), name="sampler"),
            asyncio.create_task(self._driver(), name="driver"),
        ]
        if self.robot is not None:
            self._tasks.append(asyncio.create_task(self._lease(), name="lease"))
        if self._viewer is not None:
            logger.info("--show: window 'navstack %s' opens on the first frame "
                        "(check behind the terminal!)", self.robot.url if self.robot else "drive")
        try:
            done, _ = await asyncio.wait(
                [asyncio.ensure_future(self._stop_watcher())] + self._tasks,
                return_when=asyncio.FIRST_COMPLETED)
            for t in done:
                exc = t.exception()
                if exc:
                    logger.error("task %s crashed: %r", t.get_name(), exc)
        finally:
            await self.shutdown()

    async def _stop_watcher(self) -> None:
        while not self.stop_flag:
            await asyncio.sleep(0.2)

    async def shutdown(self) -> None:
        for t in self._tasks:
            t.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)
        if self.robot is not None:
            try:
                await self.robot.stop()
                await asyncio.sleep(0.3)   # one deadman period of zero at least
                await self.robot.close()
            except Exception as exc:
                logger.error("robot shutdown incomplete: %s", exc)
        await self.source.close()
        if self._viewer is not None:
            self._viewer.close()
        logger.info("navigator stopped")
