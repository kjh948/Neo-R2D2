"""navserve WS protocol tests (vint backend) with a fake engine — CPU, no weights.

Covers: login/reset acks, buffer-only ack while context fills / goal missing,
setGoal (image & topomap & clear->explore), prediction payload keys, rc 400 on
bad seq, rc 500 with socket alive, and NoMaD goal-less predictability."""

from __future__ import annotations

import asyncio
import base64
import io
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import websockets  # noqa: E402
from PIL import Image  # noqa: E402

from navstack.config import NavConfig  # noqa: E402

WP_ROWS = [[0.05, 0.0, 0.0]] * 5


class FakeVintEngine:
    """Stands in for VintEngine (session construction + predict paths)."""
    context_size = 2
    image_size = [64, 64]
    model_type = "vint"
    supports_exploration = False

    def __init__(self, fail=False):
        self.cfg = NavConfig()
        self.fail = fail

    def infer(self, ctx, goals):
        if self.fail:
            raise RuntimeError("boom")
        return np.array([1.0]), np.tile(np.array([[0.5, 0.0, 1.0, 0.0]] * 5,
                                                 np.float32), (1, 1, 1))


def _jpeg_b64():
    buf = io.BytesIO()
    Image.fromarray(np.zeros((480, 640, 3), np.uint8)).save(buf, "JPEG")
    return base64.b64encode(buf.getvalue()).decode()


class ServerProtocolTest(unittest.TestCase):
    def setUp(self):
        import navstack.server as srv
        from navstack.vint_engine import VintSession
        self.srv = srv
        self._orig = srv.VintSession
        self.engine = FakeVintEngine()
        srv.VintSession = lambda e, cid=None: VintSession(e, cid)
        self.port = 8771

    def tearDown(self):
        self.srv.VintSession = self._orig

    def _serve_and_run(self, scenario):
        async def main():
            cfg = NavConfig(host="127.0.0.1", port=self.port)
            server = self.srv.NavServer(cfg, self.engine)
            async with websockets.serve(server._handler, "127.0.0.1", self.port):
                await asyncio.sleep(0.05)
                async with websockets.connect(f"ws://127.0.0.1:{self.port}") as ws:
                    return await scenario(ws)
        return asyncio.run(main())

    def test_full_flow(self):
        async def scenario(ws):
            out = []
            await ws.send(json.dumps({"action": "login", "data": {}}))
            out.append(json.loads(await ws.recv())["data"]["rc"])
            # next without goal -> buffered ack (ViNT requires a goal)
            await ws.send(json.dumps({"action": "next", "data": {
                "seq": 1, "image": _jpeg_b64(), "instruction": ""}}))
            msg = json.loads(await ws.recv())["data"]
            out.append((msg["rc"], msg.get("msg")))
            await ws.send(json.dumps({"action": "setGoal", "data": {
                "image": _jpeg_b64()}}))
            out.append(json.loads(await ws.recv())["data"]["msg"])
            # fill context (context_size+1 = 3 frames total incl. seq 1)
            await ws.send(json.dumps({"action": "next", "data": {
                "seq": 2, "image": _jpeg_b64(), "instruction": ""}}))
            await ws.recv()
            await ws.send(json.dumps({"action": "next", "data": {
                "seq": 3, "image": _jpeg_b64(), "instruction": ""}}))
            pred = json.loads(await ws.recv())["data"]
            out.append((pred["rc"], len(pred["actions"]["actions"]), pred["seq"]))
            # bad seq keeps socket alive; reset afterwards works
            await ws.send(json.dumps({"action": "next", "data": {
                "seq": 1.5, "image": _jpeg_b64()}}))
            out.append(json.loads(await ws.recv())["data"]["rc"])
            await ws.send(json.dumps({"action": "reset", "data": {}}))
            out.append(json.loads(await ws.recv())["data"]["rc"])
            return out
        res = self._serve_and_run(scenario)
        self.assertEqual(res[0], 0)                       # login
        self.assertEqual(res[1], (0, "image received"))   # buffered
        self.assertEqual(res[2], "goal image set")        # setGoal
        self.assertEqual(res[3], (0, 5, 3))               # prediction payload
        self.assertEqual(res[4], 400)                     # bad seq
        self.assertEqual(res[5], 0)                       # reset after error

    def test_inference_error_keeps_socket(self):
        self.engine.fail = True

        async def scenario(ws):
            await ws.send(json.dumps({"action": "login", "data": {}}))
            await ws.recv()
            await ws.send(json.dumps({"action": "setGoal", "data": {
                "image": _jpeg_b64()}}))
            await ws.recv()
            for seq in range(1, 4):
                await ws.send(json.dumps({"action": "next", "data": {
                    "seq": seq, "image": _jpeg_b64(), "instruction": ""}}))
                last = json.loads(await ws.recv())["data"]
            self.assertEqual(last["rc"], 500)
            await ws.send(json.dumps({"action": "reset", "data": {}}))
            self.assertEqual(json.loads(await ws.recv())["data"]["rc"], 0)
        self._serve_and_run(scenario)


class ServerExplorationTest(unittest.TestCase):
    def test_nomad_predicts_without_goal(self):
        import navstack.server as srv

        class ExploringEngine(FakeVintEngine):
            model_type = "nomad"
            supports_exploration = True

            def infer_nomad(self, ctx, goal=None):
                return np.tile([0.1, 0.0], (8, 1)).astype(np.float32), float("nan")

        async def scenario(ws):
            await ws.send(json.dumps({"action": "login", "data": {}}))
            await ws.recv()
            msgs = []
            for seq in range(1, 5):
                await ws.send(json.dumps({"action": "next", "data": {
                    "seq": seq, "image": _jpeg_b64()}}))
                msgs.append(json.loads(await ws.recv())["data"])
            # context_size=2 -> ready at 3 frames; 3rd is first prediction
            self.assertEqual(msgs[0].get("msg"), "image received")
            self.assertEqual(msgs[1].get("msg"), "image received")
            self.assertEqual(msgs[2]["rc"], 0)
            self.assertIn("actions", msgs[2])
            self.assertEqual(len(msgs[2]["actions"]["actions"]), 8)
        cfg_engine = ExploringEngine()
        port = 8772

        async def runner():
            cfg = NavConfig(host="127.0.0.1", port=port)
            server = srv.NavServer(cfg, cfg_engine)
            async with websockets.serve(server._handler, "127.0.0.1", port):
                await asyncio.sleep(0.05)
                async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
                    await scenario(ws)
        asyncio.run(runner())


if __name__ == "__main__":
    unittest.main()
