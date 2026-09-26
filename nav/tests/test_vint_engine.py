"""vint backend tests: payload wiring with random weights + server protocol with a fake.

No published checkpoint is fetched here (Google Drive, gated in this sandbox), so
the engine test builds a RANDOMLY-initialized ViNT and checks only the plumbing
that is ours -- tensor shaping, the (T,3) waypoint payload, goal/context gating,
and server dispatch. Navigation QUALITY requires the real weights (see
scripts/setup_vint.md)."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from navstack.config import NavConfig  # noqa: E402

REPO = Path(__file__).resolve().parents[1] / "visualnav-transformer"
HAS_VINT = REPO.is_dir()


def _tiny_vint_config(tmp: Path) -> Path:
    """A vint.yaml subset with a small context so the test is fast on CPU."""
    cfg = tmp / "vint.yaml"
    cfg.write_text(
        "model_type: vint\n"
        "obs_encoder: efficientnet-b0\n"
        "obs_encoding_size: 512\n"
        "mha_num_attention_heads: 4\n"
        "mha_num_attention_layers: 4\n"
        "mha_ff_dim_factor: 4\n"
        "late_fusion: False\n"
        "normalize: True\n"
        "context_type: temporal\n"
        "context_size: 2\n"          # small for a fast test\n"
        "learn_angle: True\n"
        "len_traj_pred: 5\n"
        "image_size: [64, 64]\n"     # W,H\n"
        "goal_type: image\n")
    return cfg


@unittest.skipUnless(HAS_VINT, "visualnav-transformer not cloned")
class VintEngineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import torch
        from vint_train.models.vint.vint import ViNT
        cls.tmp = tempfile.TemporaryDirectory()
        cfg_path = _tiny_vint_config(Path(cls.tmp.name))
        ckpt_path = cfg_path.parent / "random_vint.pth"
        model = ViNT(context_size=2, len_traj_pred=5, learn_angle=True,
                     obs_encoder="efficientnet-b0", obs_encoding_size=512,
                     late_fusion=False, mha_num_attention_heads=4,
                     mha_num_attention_layers=4, mha_ff_dim_factor=4)
        torch.save({"model": model}, ckpt_path)

        cfg = NavConfig(backend="vint", vint_config=str(cfg_path), vint_ckpt=str(ckpt_path))
        cfg.load()
        from navstack.vint_engine import VintEngine
        cls.engine = VintEngine(cfg)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_predict_payload_shape_and_keys(self):
        ses = _make_session(self.engine)
        ses.goal_img = np.full((80, 80, 3), 120, np.uint8)
        for _ in range(3):                      # context_size+1 == 3
            ses.observe(np.zeros((80, 80, 3), np.uint8))
        data = ses.predict()
        self.assertEqual(data["rc"], 0)
        self.assertIn("actions", data)
        rows = data["actions"]["actions"]
        self.assertEqual(len(rows), 5)          # len_traj_pred
        self.assertEqual(len(rows[0]), 3)       # [fwd, lat, yaw]
        self.assertIn("stop", data)

    def test_multi_goal_batching(self):
        # regression: several goals stack on the BATCH axis, context repeats to
        # match -- this shape bug raised inside the model's forward cat before fix.
        ctx = [np.zeros((80, 80, 3), np.uint8) for _ in range(3)]
        goals = [np.full((80, 80, 3), v, np.uint8) for v in (40, 80, 120)]
        dist, actions = self.engine.infer(ctx, goals)
        self.assertEqual(dist.shape, (3,))
        self.assertEqual(actions.shape[0], 3)
        self.assertEqual(actions.shape[1], 5)   # len_traj_pred
        self.assertEqual(actions.shape[2], 4)   # x,y,cos,sin

    def test_context_not_ready_raises(self):
        from navstack.vint_engine import VintSession
        ses = VintSession(self.engine)
        ses.goal_img = np.zeros((80, 80, 3), np.uint8)
        ses.observe(np.zeros((80, 80, 3), np.uint8))
        with self.assertRaises(ValueError):
            ses.predict()

    def test_no_goal_raises(self):
        ses = _make_session(self.engine)
        for _ in range(3):
            ses.observe(np.zeros((80, 80, 3), np.uint8))
        with self.assertRaises(ValueError):
            ses.predict()


class _FakeEngine:
    """Bypass torch: exercise VintSession topomap logic with canned distances."""
    context_size = 2
    image_size = [64, 64]

    def __init__(self, distances):
        self.cfg = NavConfig(backend="vint", close_threshold_m=0.5,
                             subgoal_radius=3, wp_scale_m=0.75, y_sign=1.0)
        self._d = distances

    def infer(self, ctx, goals):
        n = len(goals)
        dist = np.array(self._d[:n], dtype=np.float32)
        actions = np.tile(np.array([[0.5, 0.1, 1.0, 0.0]] * 5, dtype=np.float32), (n, 1, 1))
        return dist, actions


def _make_session(engine):
    from navstack.vint_engine import VintSession
    return VintSession(engine)


class VintTopomapTest(unittest.TestCase):
    def _session_with_nodes(self, distances):
        eng = _FakeEngine(distances)
        ses = _make_session(eng)
        ses.nodes = [np.zeros((10, 10, 3), np.uint8)] * 6
        for _ in range(eng.context_size + 1):
            ses.observe(np.zeros((10, 10, 3), np.uint8))
        return ses

    def test_hop_forward_when_close(self):
        ses = self._session_with_nodes([0.3, 4.0, 5.0, 6.0])  # subgoal 0 within threshold
        before = ses.closest
        ses._predict_topomap([np.zeros((2, 2, 3), np.uint8)] * 3)
        self.assertEqual(ses.closest, before + 1)

    def test_stops_at_last_node(self):
        ses = self._session_with_nodes([0.4, 0.4, 0.4, 0.4])
        ses.closest = len(ses.nodes) - 1
        data = ses._predict_topomap([np.zeros((2, 2, 3), np.uint8)] * 3)
        self.assertTrue(data["stop"])

    def test_waypoint_y_sign(self):
        eng = _FakeEngine([1.0])
        eng.cfg.y_sign = -1.0
        ses = _make_session(eng)
        ses.goal_img = np.zeros((2, 2, 3), np.uint8)
        for _ in range(eng.context_size + 1):
            ses.observe(np.zeros((2, 2, 3), np.uint8))
        rows = ses.predict()["actions"]["actions"]
        self.assertLess(rows[-1][1], 0.0)      # +y lat flipped by y_sign=-1


if __name__ == "__main__":
    unittest.main()
