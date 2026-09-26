"""vint backend tests: real torch plumbing (random weights) + NoMaD exploration.

Weights from Google Drive are NOT required except for the marked real-weights
test; random-initialized architectures validate tensor shapes, payload
mapping, context/goal gating and the topomap state machine."""

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
NOMAD_CKPT = (Path(__file__).resolve().parents[1]
              / "models/vint_weights/checkpoints/nomad.pth")


def _tiny_vint_config(tmp: Path) -> Path:
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
        "context_size: 2\n"
        "learn_angle: True\n"
        "len_traj_pred: 5\n"
        "image_size: [64, 64]\n"
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
        cfg = NavConfig(vint_config=str(cfg_path), vint_ckpt=str(ckpt_path))
        from navstack.vint_engine import VintEngine
        cls.engine = VintEngine(cfg.load())

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def _session(self, goal=True):
        from navstack.vint_engine import VintSession
        ses = VintSession(self.engine)
        if goal:
            ses.goal_img = np.full((80, 80, 3), 120, np.uint8)
        return ses

    def test_predict_payload_shape_and_keys(self):
        ses = self._session()
        for _ in range(3):
            ses.observe(np.zeros((80, 80, 3), np.uint8))
        data = ses.predict()
        rows = data["actions"]["actions"]
        self.assertEqual(data["rc"], 0)
        self.assertEqual((len(rows), len(rows[0])), (5, 3))
        self.assertIn("stop", data)

    def test_multi_goal_batching(self):
        ctx = [np.zeros((80, 80, 3), np.uint8) for _ in range(3)]
        goals = [np.full((80, 80, 3), v, np.uint8) for v in (40, 80, 120)]
        dist, actions = self.engine.infer(ctx, goals)
        self.assertEqual(dist.shape, (3,))
        self.assertEqual(actions.shape[:2], (3, 5))

    def test_context_not_ready_raises(self):
        ses = self._session()
        ses.observe(np.zeros((80, 80, 3), np.uint8))
        with self.assertRaises(ValueError):
            ses.predict()

    def test_no_goal_raises_for_vint(self):
        ses = self._session(goal=False)
        for _ in range(3):
            ses.observe(np.zeros((80, 80, 3), np.uint8))
        with self.assertRaises(ValueError):
            ses.predict()


class _FakeEngine:
    """Canned distances for the topomap state machine (no torch needed)."""
    context_size = 2
    image_size = [64, 64]
    model_type = "vint"
    supports_exploration = False

    def __init__(self, distances):
        self.cfg = NavConfig(close_threshold_m=0.5, subgoal_radius=3,
                             wp_scale_m=0.75, y_sign=1.0)
        self._d = distances

    def infer(self, ctx, goals):
        n = len(goals)
        dist = np.array(self._d[:n], dtype=np.float32)
        actions = np.tile(np.array([[0.5, 0.1, 1.0, 0.0]] * 5, np.float32), (n, 1, 1))
        return dist, actions


@unittest.skipUnless(HAS_VINT, "visualnav-transformer not cloned")
class VintTopomapTest(unittest.TestCase):
    def _session(self, distances):
        from navstack.vint_engine import VintSession
        eng = _FakeEngine(distances)
        ses = VintSession(eng)
        ses.nodes = [np.zeros((10, 10, 3), np.uint8)] * 6
        for _ in range(eng.context_size + 1):
            ses.observe(np.zeros((10, 10, 3), np.uint8))
        return ses

    def test_hop_forward_when_close(self):
        ses = self._session([0.3, 4.0, 5.0, 6.0])
        before = ses.closest
        ses._predict_topomap([np.zeros((2, 2, 3), np.uint8)] * 3)
        self.assertEqual(ses.closest, before + 1)

    def test_stops_at_last_node(self):
        ses = self._session([0.4, 0.4, 0.4, 0.4])
        ses.closest = len(ses.nodes) - 1
        self.assertTrue(ses._predict_topomap(
            [np.zeros((2, 2, 3), np.uint8)] * 3)["stop"])

    def test_waypoint_y_sign(self):
        from navstack.vint_engine import VintSession
        eng = _FakeEngine([1.0])
        eng.cfg.y_sign = -1.0
        ses = VintSession(eng)
        ses.goal_img = np.zeros((2, 2, 3), np.uint8)
        for _ in range(eng.context_size + 1):
            ses.observe(np.zeros((2, 2, 3), np.uint8))
        rows = ses.predict()["actions"]["actions"]
        self.assertLess(rows[-1][1], 0.0)


@unittest.skipUnless(HAS_VINT and NOMAD_CKPT.is_file(),
                     "NoMaD build or checkpoint unavailable")
class NoMadRealWeightsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from navstack.vint_engine import VintEngine
        cfg = NavConfig(
            vint_config=str(REPO / "train/config/nomad.yaml"),
            vint_ckpt=str(NOMAD_CKPT))
        cls.engine = VintEngine(cfg.load())

    def test_exploration_produces_metric_waypoints(self):
        from navstack.vint_engine import VintSession
        ses = VintSession(self.engine)
        self.assertTrue(ses.has_goal())          # exploration: always predictable
        rng = np.random.default_rng(3)
        for _ in range(self.engine.context_size + 1):
            ses.observe(rng.integers(0, 255, (96, 128, 3), dtype=np.uint8))
        data = ses.predict()
        rows = np.array(data["actions"]["actions"])
        self.assertEqual(rows.shape, (8, 3))     # len_traj_pred, yaw==0
        self.assertTrue(np.isfinite(rows).all())
        self.assertEqual(rows[:, 2].tolist(), [0.0] * 8)
        # meters-scale: cumulative radius plausible (<5 m), not normalized junk
        self.assertLess(float(np.abs(rows).max()), 5.0)


if __name__ == "__main__":
    unittest.main()
