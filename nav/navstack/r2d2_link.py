"""Async WebSocket link to the R2D2 host API (port 8887) and video stream (12121).

Protocol facts (r2d2/api.py, server.py, verified in r2d2/README.md):
  * connect ws://<robot>:8887/ and send ``grantAccess`` within 10 s
    (ESTABLISH_TIMEOUT) or the socket is dropped;
  * ``user_control enable:true`` claims the stick; the 12 s CONTROL_TIMEOUT
    lease is re-armed by ANY command we send -- refresh every ~5 s;
  * motion commands (``move`` etc.) get NO reply and must be re-sent ~3 Hz
    (deadman, like the teleop app);
  * battery/charging ride on the ``gin`` push and the grantAccess reply;
  * while charging, the MCU-side interlock silently drops ``move`` in the
    firmware/Commander layer -- we also refuse to send (surfaced in logs).

The video port streams binary JPEGs at 10 fps to exactly one viewer after a
text hello; opening it pauses face detection on the robot -- documented side
effect, intended for our use.
"""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Optional

from websockets.asyncio.client import connect

logger = logging.getLogger("navstack.r2d2")

GRANT_TIMEOUT_S = 10.0
LEASE_REFRESH_S = 5.0
MOVE_DEADMAN_S = 0.3


class R2D2Link:
    """Holds the 8887 command socket; caller drives it with send_* calls."""

    def __init__(self, host: str, port: int = 8887, uuid: str = "navstack-1",
                 device_name: str = "NavStack"):
        self.url = f"ws://{host}:{port}/"
        self.uuid = uuid
        self.device_name = device_name
        self.ws = None
        self.battery: Optional[int] = None
        self.charging: Optional[int] = None
        self._reader: Optional[asyncio.Task] = None
        self._last_lease = 0.0

    async def connect(self) -> dict:
        self.ws = await connect(self.url, max_size=2 ** 22)
        grant = {"cmd": "grantAccess", "seq": 1, "uuid": self.uuid,
                 "device_name": self.device_name}
        await self.ws.send(json.dumps(grant))
        reply = json.loads(await asyncio.wait_for(self.ws.recv(), GRANT_TIMEOUT_S))
        if reply.get("resultCode", reply.get("cmd")) not in (0, "grantAccess"):
            raise ConnectionError(f"grantAccess rejected: {reply}")
        robot = reply.get("robot") or {}
        self.battery = robot.get("battery")
        self.charging = robot.get("charging")
        self._reader = asyncio.create_task(self._read_loop(), name="r2d2-reader")
        logger.info("r2d2 granted access (battery=%s charging=%s)", self.battery, self.charging)
        return robot

    async def _read_loop(self) -> None:
        assert self.ws is not None
        try:
            async for raw in self.ws:
                try:
                    msg = json.loads(raw)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
                if msg.get("cmd") == "gin":
                    robot = msg.get("robot") or {}
                    if "battery" in robot:
                        self.battery = robot["battery"]
                    if "charging" in robot:
                        self.charging = robot["charging"]
        except Exception as exc:  # socket closed
            logger.debug("r2d2 reader ended: %s", exc)

    async def claim_control(self) -> None:
        await self._send({"cmd": "user_control", "enable": True})

    async def move(self, power: int, angle: int) -> None:
        if self.charging:
            logger.warning("robot is charging; move(%s,%s) suppressed", power, angle)
            return
        await self._send({"cmd": "move", "power": int(power), "angle": int(angle)})

    async def stop(self) -> None:
        await self.move(0, 0)

    async def release_control(self) -> None:
        await self._send({"cmd": "user_control", "enable": False})

    async def refresh_lease(self) -> None:
        """Re-arm the 12 s control timeout by pinging user_control every 5 s."""
        now = asyncio.get_running_loop().time()
        if now - self._last_lease >= LEASE_REFRESH_S:
            await self.claim_control()
            self._last_lease = now

    async def _send(self, obj: dict) -> None:
        if self.ws is None:
            raise ConnectionError("r2d2 link not connected")
        await self.ws.send(json.dumps(obj, separators=(",", ":")))

    async def close(self) -> None:
        try:
            await self.release_control()
        except Exception:
            pass
        if self._reader:
            self._reader.cancel()
        if self.ws:
            await self.ws.close()
            self.ws = None


class R2D2VideoSource:
    """Frame source from the robot's :12121 binary JPEG stream (10 fps)."""

    def __init__(self, host: str, port: int = 12121):
        self.url = f"ws://{host}:{port}/"
        self.ws = None
        self.latest: Optional[bytes] = None

    async def connect(self) -> None:
        self.ws = await connect(self.url, max_size=2 ** 22)
        hello = await asyncio.wait_for(self.ws.recv(), 5.0)
        logger.info("r2d2 video hello: %r", hello)
        asyncio.create_task(self._pump(), name="r2d2-video")

    async def _pump(self) -> None:
        assert self.ws is not None
        async for frame in self.ws:
            if isinstance(frame, (bytes, bytearray)):
                self.latest = bytes(frame)

    async def get_jpeg(self) -> Optional[bytes]:
        return self.latest

    async def close(self) -> None:
        if self.ws:
            await self.ws.close()
            self.ws = None
