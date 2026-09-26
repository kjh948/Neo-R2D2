"""navserve: WebSocket server speaking the lightnav-serve protocol over llama.cpp.

Protocol (docs/PROTOCOL.md, verified against tests/serving/test_ws_server.py):
  {"action":"login","data":{"clientId"?}}      -> {rc:0,msg:"ok"}
  {"action":"reset","data":{}}                 -> {rc:0,msg:"ok"}
  {"action":"next","data":{seq,image,instruction}}
      instruction empty      -> {rc:0,seq,msg:"image received"}   (buffer-only)
      prediction             -> {rc:0,seq,actions:{step,actions},stop,visible,
                                 latency_ms,timings_ms,raw_text,pointing?}
      rc 400 malformed / rc 500 decode failure; connection stays open.

Warm-up: one synthetic inference BEFORE binding the port (port open == ready),
like lightnav-serve. A ready_file can be polled by scripts instead.
"""

from __future__ import annotations

import asyncio
import json
import logging
import signal
from pathlib import Path
from typing import Any, Dict, Optional

import websockets

from .config import NavConfig
from .engine import LlamaServerError, NavEngine, NavSession
from .frames import decode_b64

logger = logging.getLogger("navstack.server")

MAX_MSG_BYTES = 64 * 1024 * 1024


class NavServer:
    def __init__(self, cfg: NavConfig, engine: NavEngine):
        self.cfg = cfg
        self.engine = engine
        self._loop: Optional[asyncio.AbstractEventLoop] = None

    def _new_session(self, client_id=None):
        if self.cfg.backend == "vint":
            from .vint_engine import VintSession
            return VintSession(self.engine, client_id)
        return NavSession(self.engine, client_id)

    async def _handler(self, ws) -> None:
        session = None
        async for raw in ws:
            try:
                msg = json.loads(raw)
                action = msg.get("action")
                data = msg.get("data") or {}
                if not isinstance(data, dict):
                    raise ValueError("data must be an object")
                if action == "login":
                    session = self._new_session(data.get("clientId"))
                    await ws.send(json.dumps(
                        {"action": "login", "data": {"rc": 0, "msg": "ok"}}))
                elif action == "reset":
                    if session is not None:
                        session.reset()
                    await ws.send(json.dumps(
                        {"action": "reset", "data": {"rc": 0, "msg": "ok"}}))
                elif action == "setGoal":  # vint backend extension (goal image/topomap)
                    if session is None or not hasattr(session, "set_goal"):
                        await ws.send(json.dumps({"action": "setGoal", "data": {
                            "rc": 400, "msg": "goal not supported by this backend"}}))
                        continue
                    note = session.set_goal(data)
                    await ws.send(json.dumps(
                        {"action": "setGoal", "data": {"rc": 0, "msg": note}}))
                elif action == "next":
                    session = await self._handle_next(ws, session, data)
                else:
                    await ws.send(json.dumps(
                        {"action": action, "data": {"rc": 400, "msg": f"unknown action {action!r}"}}))
            except (json.JSONDecodeError, ValueError) as exc:
                await ws.send(json.dumps(
                    {"action": "err", "data": {"rc": 400, "msg": str(exc)}}))

    async def _handle_next(self, ws, session: Optional[NavSession], data: Dict[str, Any]):
        if session is None:  # sessions are created lazily, like lightnav-serve
            session = self._new_session()
        seq = data.get("seq")
        if seq is None or isinstance(seq, bool) or not float(seq).is_integer():
            await ws.send(json.dumps(
                {"action": "next", "data": {"rc": 400, "msg": "seq must be an integer"}}))
            return session
        seq = int(seq)
        goal_b64 = data.get("goal")
        if goal_b64 and hasattr(session, "set_goal"):
            session.set_goal({"image": goal_b64})
        image_b64 = data.get("image")
        instruction = data.get("instruction") or ""
        if not image_b64 or not isinstance(image_b64, str):
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 400, "seq": seq, "msg": "image must be a non-empty base64 string"}}))
            return session
        try:
            frame = decode_b64(image_b64)
        except Exception as exc:
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 400, "seq": seq, "msg": f"image decode failed: {exc}"}}))
            return session
        session.observe(frame)
        # llama protocol: empty instruction = buffer-only. vint: the goal replaces
        # the instruction, so once a goal exists every frame is a prediction.
        wants_predict = bool(instruction) or (
            self.cfg.backend == "vint" and getattr(session, "has_goal", lambda: False)())
        if not wants_predict:
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 0, "seq": seq, "msg": "image received"}}))
            return session

        t0 = asyncio.get_running_loop().time()
        try:
            # run in a thread: llama-server calls block for the whole prefill
            resp = await asyncio.to_thread(session.predict, instruction)
        except ValueError as exc:
            if "context filling" in str(exc) or "no goal" in str(exc):
                await ws.send(json.dumps({"action": "next", "data": {
                    "rc": 0, "seq": seq, "msg": str(exc)}}))
                return session
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 500, "seq": seq, "msg": f"decode error: {exc}"}}))
            return session
        except LlamaServerError as exc:
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 500, "seq": seq, "msg": f"backend error: {exc}"}}))
            return session
        latency_ms = (asyncio.get_running_loop().time() - t0) * 1000.0
        resp["seq"] = seq
        resp["latency_ms"] = round(latency_ms, 1)
        await ws.send(json.dumps({"action": "next", "data": resp}))
        return session

    async def warmup(self) -> None:
        """Synthetic prediction before the port binds (port open == ready)."""
        import numpy as np
        try:
            if self.cfg.backend == "vint":
                session = self._new_session("warmup")
                goal = np.full((self.engine.image_size[1], self.engine.image_size[0], 3),
                               90, np.uint8)
                session.goal_img = goal
                for _ in range(self.engine.context_size + 1):
                    session.observe(np.zeros((120, 160, 3), np.uint8))
                await asyncio.to_thread(session.predict)
            else:
                session = NavSession(self.engine, "warmup")
                h, w = self.cfg.video_size
                session.observe(np.zeros((h, w, 3), dtype=np.uint8))
                await asyncio.to_thread(session.predict, "warm up")
            logger.info("warm-up inference ok")
        except Exception as exc:
            logger.warning("warm-up inference failed (continuing): %s", exc)

    async def run(self) -> None:
        await self.warmup()
        if self.cfg.ready_file:
            Path(self.cfg.ready_file).write_text(str(self.cfg.port))
        async with websockets.serve(
                self._handler, self.cfg.host, self.cfg.port, max_size=MAX_MSG_BYTES):
            logger.info("navserve listening on ws://%s:%d (backend=%s task=%s preset=%s)",
                        self.cfg.host, self.cfg.port, self.cfg.backend,
                        self.cfg.task, self.cfg.preset)
            await asyncio.Future()  # run forever


def build_engine(cfg: NavConfig):
    """Backend factory: llama (external llama-server) or vint (in-process torch)."""
    if cfg.backend == "vint":
        from .vint_engine import VintEngine
        return VintEngine(cfg)
    engine = NavEngine(cfg)
    logger.info("waiting for llama-server at %s ...", cfg.llama_url)
    engine.wait_ready()
    return engine


def serve(cfg: NavConfig) -> None:
    logging.basicConfig(level=getattr(logging, cfg.log_level.upper(), logging.INFO))
    engine = build_engine(cfg)
    server = NavServer(cfg, engine)
    try:
        asyncio.run(server.run())
    except KeyboardInterrupt:
        pass
