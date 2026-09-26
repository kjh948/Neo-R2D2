"""WS protocol conformance tests for navserve with a fake engine (CPU, no weights).

Checks the lightnav-serve contract used by robot_deploy clients:
login/reset acks, buffer-only ack on empty instruction, prediction payload
keys, rc 400 on malformed input, rc 500 on decode failure with a live socket.
"""

from __future__ import annotations

import asyncio
import base64
import json
import sys
import unittest
from io import BytesIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import websockets  # noqa: E402
from PIL import Image  # noqa: E402

from navstack.config import NavConfig  # noqa: E402
from navstack.server import NavServer  # noqa: E402

WP = np.tile(np.array([[0.05, 0.0, 0.0]], dtype=np.float32), (10, 1))


class FakeEngine:
    """Stands in for NavEngine: predict() returns canned payloads."""

    def __init__(self, fail: bool = False):
        self.cfg = NavConfig(video_size=[64, 64], fps=4.0, tiers=[])
        self.fail = fail
        self.calls = 0

    def health(self, timeout: float = 5.0) -> bool:
        return True


class FakeSession:
    def __init__(self, engine, client_id=None):
        self.engine = engine
        self.last_frame_hw = (480, 640)

    def reset(self):
        pass

    def observe(self, frame):
        return 0

    def predict(self, instruction):
        self.engine.calls += 1
        if self.engine.fail:
            raise ValueError("missing act levels")
        return {
            "rc": 0, "actions": {"step": self.engine.calls, "actions": WP.tolist()},
            "stop": False, "visible": None, "raw_text": "<act_l0_1>",
            "timings_ms": {"total_ms": 1.0},
        }


def _patch_sessions(test, fail=False):
    engine = FakeEngine(fail=fail)
    import navstack.server as srv
    orig = srv.NavSession
    srv.NavSession = lambda e, cid=None: FakeSession(engine)  # noqa: E731
    return engine, orig


class ProtocolTest(unittest.TestCase):
    def setUp(self):
        asyncio.set_event_loop(asyncio.new_event_loop())
        self.session = None

    def _run(self, coro):
        return asyncio.get_event_loop().run_until_complete(coro)

    def _jpeg_b64(self) -> str:
        buf = BytesIO()
        Image.fromarray(np.zeros((480, 640, 3), np.uint8)).save(buf, "JPEG")
        return base64.b64encode(buf.getvalue()).decode()

    async def _serve(self, engine_factory, port):
        cfg = NavConfig(host="127.0.0.1", port=port, video_size=[64, 64], tiers=[])
        server = NavServer(cfg, engine_factory())

        async def handler(ws):
            async for raw in ws:
                await self.dispatch(server, ws, raw)

        async with websockets.serve(handler, "127.0.0.1", port):
            await asyncio.sleep(0.05)
            async with websockets.connect(f"ws://127.0.0.1:{port}") as client:
                return await self.session_roundtrip(client)

    async def dispatch(self, server, ws, raw):
        msg = json.loads(raw)
        action, data = msg.get("action"), msg.get("data") or {}
        if self.session is None:  # sessions are created lazily by _handle_next too
            self.session = FakeSession(server.engine)
        if action == "next":
            return await server._handle_next(ws, self.session, data)
        if action == "reset":
            await ws.send(json.dumps({"action": "reset", "data": {"rc": 0, "msg": "ok"}}))
        if action == "login":
            await ws.send(json.dumps({"action": "login", "data": {"rc": 0, "msg": "ok"}}))

    async def session_roundtrip(self, client):
        await client.send(json.dumps({"action": "login", "data": {"clientId": "t"}}))
        login = json.loads(await client.recv())
        self.assertEqual(login["data"]["rc"], 0)

        # buffer-only: empty instruction
        await client.send(json.dumps({"action": "next", "data": {
            "seq": 1, "image": self._jpeg_b64(), "instruction": ""}}))
        ack = json.loads(await client.recv())
        self.assertEqual(ack["data"]["msg"], "image received")
        self.assertEqual(ack["data"]["seq"], 1)

        # prediction
        await client.send(json.dumps({"action": "next", "data": {
            "seq": 2, "image": self._jpeg_b64(), "instruction": "go to the door"}}))
        pred = json.loads(await client.recv())
        d = pred["data"]
        self.assertEqual(d["rc"], 0)
        self.assertEqual(d["seq"], 2)
        self.assertEqual(len(d["actions"]["actions"]), 10)
        self.assertIn("stop", d)
        self.assertIn("latency_ms", d)

        # malformed: float seq
        await client.send(json.dumps({"action": "next", "data": {
            "seq": 1.5, "image": self._jpeg_b64(), "instruction": "x"}}))
        bad = json.loads(await client.recv())
        self.assertEqual(bad["data"]["rc"], 400)
        return True

    def test_protocol_happy_and_malformed(self):
        engine, orig = _patch_sessions(self)
        import navstack.server as srv
        srv.NavSession = lambda e, cid=None: FakeSession(engine)
        try:
            self.assertTrue(self._run(self._serve(lambda: engine, 8761)))
        finally:
            srv.NavSession = orig

    def test_decode_error_keeps_connection(self):
        engine = FakeEngine(fail=True)
        import navstack.server as srv
        orig = srv.NavSession
        srv.NavSession = lambda e, cid=None: FakeSession(engine)

        async def run():
            cfg = NavConfig(host="127.0.0.1", port=8762, tiers=[])
            server = NavServer(cfg, engine)

            async def handler(ws):
                async for raw in ws:
                    await self.dispatch(server, ws, raw)

            async with websockets.serve(handler, "127.0.0.1", 8762):
                await asyncio.sleep(0.05)
                async with websockets.connect("ws://127.0.0.1:8762") as c:
                    await c.send(json.dumps({"action": "next", "data": {
                        "seq": 3, "image": self._jpeg_b64(), "instruction": "walk"}}))
                    r1 = json.loads(await c.recv())
                    self.assertEqual(r1["data"]["rc"], 500)
                    # socket stays usable
                    await c.send(json.dumps({"action": "reset", "data": {}}))
                    r2 = json.loads(await c.recv())
                    self.assertEqual(r2["data"]["rc"], 0)
        try:
            self._run(run())
        finally:
            srv.NavSession = orig


if __name__ == "__main__":
    unittest.main()
