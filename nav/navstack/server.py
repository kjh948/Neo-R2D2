"""navserve: WebSocket navigation server (visualnav-transformer backend).

Protocol (lightnav-serve compatible core + goal extensions):
  {"action":"login","data":{"clientId"?}}      -> {rc:0,msg:"ok"}
  {"action":"reset","data":{}}                 -> {rc:0,msg:"ok"}
  {"action":"setGoal","data":{"image"|"topomap"}}
                                               -> {rc:0,msg:<note>}
  {"action":"next","data":{seq,image,goal?,instruction?}}
      not predictable yet   -> {rc:0,seq,msg:"image received"}
      prediction            -> {rc:0,seq,actions:{step,actions},stop,visible,
                                latency_ms,raw_text,subgoal_*?}
      rc 400 malformed / rc 500 failure; socket stays open.

NoMaD checkpoints run GOAL-LESS: without a goal every frame yields an
exploration action (wander + collision avoidance). ViNT/GNM require a goal
(image or topomap) before predictions start.

Warm-up: one synthetic inference before the port binds (port open == ready).
"""

from __future__ import annotations

import asyncio
import json
import logging
from pathlib import Path
from typing import Any, Dict, Optional

import websockets

from .config import NavConfig
from .vint_engine import VintEngine, VintSession

logger = logging.getLogger("navstack.server")

MAX_MSG_BYTES = 64 * 1024 * 1024


class NavServer:
    def __init__(self, cfg: NavConfig, engine: VintEngine):
        self.cfg = cfg
        self.engine = engine

    async def _handler(self, ws) -> None:
        session: Optional[VintSession] = None
        async for raw in ws:
            try:
                msg = json.loads(raw)
                action = msg.get("action")
                data = msg.get("data") or {}
                if not isinstance(data, dict):
                    raise ValueError("data must be an object")
                if action == "login":
                    session = VintSession(self.engine, data.get("clientId"))
                    await ws.send(json.dumps(
                        {"action": "login", "data": {"rc": 0, "msg": "ok"}}))
                elif action == "reset":
                    if session is not None:
                        session.reset()
                    await ws.send(json.dumps(
                        {"action": "reset", "data": {"rc": 0, "msg": "ok"}}))
                elif action == "setGoal":
                    if session is None:
                        session = VintSession(self.engine)
                    note = session.set_goal(data)
                    await ws.send(json.dumps(
                        {"action": "setGoal", "data": {"rc": 0, "msg": note}}))
                elif action == "next":
                    session = await self._handle_next(ws, session, data)
                else:
                    await ws.send(json.dumps(
                        {"action": action, "data": {"rc": 400,
                                                    "msg": f"unknown action {action!r}"}}))
            except (json.JSONDecodeError, ValueError) as exc:
                await ws.send(json.dumps(
                    {"action": "err", "data": {"rc": 400, "msg": str(exc)}}))

    async def _handle_next(self, ws, session: Optional[VintSession],
                           data: Dict[str, Any]) -> Optional[VintSession]:
        if session is None:                      # lazy session, lightnav-style
            session = VintSession(self.engine)
        seq = data.get("seq")
        if seq is None or isinstance(seq, bool) or not float(seq).is_integer():
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 400, "msg": "seq must be an integer"}}))
            return session
        seq = int(seq)
        goal_b64 = data.get("goal")
        if goal_b64:
            session.set_goal({"image": goal_b64})
        image_b64 = data.get("image")
        instruction = data.get("instruction") or ""
        if not image_b64 or not isinstance(image_b64, str):
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 400, "seq": seq, "msg": "image must be a non-empty base64 string"}}))
            return session
        from .frames import decode_b64
        try:
            frame = decode_b64(image_b64)
        except Exception as exc:
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 400, "seq": seq, "msg": f"image decode failed: {exc}"}}))
            return session
        session.observe(frame)

        # Prediction gates: has_goal() is always True for NoMaD (exploration);
        # ViNT/GNM need a goal. Context must be full either way.
        if not session.has_goal() or len(session.frames) < session.ready_frames:
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 0, "seq": seq, "msg": "image received"}}))
            return session

        t0 = asyncio.get_running_loop().time()
        try:
            resp = await asyncio.to_thread(session.predict, instruction)
        except Exception as exc:
            await ws.send(json.dumps({"action": "next", "data": {
                "rc": 500, "seq": seq, "msg": f"inference error: {exc}"}}))
            return session
        resp["seq"] = seq
        resp["latency_ms"] = round(
            (asyncio.get_running_loop().time() - t0) * 1000.0, 1)
        await ws.send(json.dumps({"action": "next", "data": resp}))
        return session

    async def warmup(self) -> None:
        """Synthetic prediction before the port binds."""
        import numpy as np
        try:
            session = VintSession(self.engine, "warmup")
            w, h = self.engine.image_size
            for _ in range(self.engine.context_size + 1):
                session.observe(np.zeros((h * 2, w * 2, 3), np.uint8))
            await asyncio.to_thread(session.predict)
            logger.info("warm-up inference ok")
        except Exception as exc:
            logger.warning("warm-up inference failed (continuing): %s", exc)

    async def run(self) -> None:
        await self.warmup()
        if self.cfg.ready_file:
            Path(self.cfg.ready_file).write_text(str(self.cfg.port))
        async with websockets.serve(
                self._handler, self.cfg.host, self.cfg.port, max_size=MAX_MSG_BYTES):
            logger.info("navserve listening on ws://%s:%d (model=%s ctx=%d)",
                        self.cfg.host, self.cfg.port, self.engine.model_type,
                        self.engine.context_size)
            await asyncio.Future()  # run forever


def serve(cfg: NavConfig) -> None:
    logging.basicConfig(level=getattr(logging, cfg.log_level.upper(), logging.INFO))
    engine = VintEngine(cfg.load())
    server = NavServer(cfg, engine)
    try:
        asyncio.run(server.run())
    except KeyboardInterrupt:
        pass
