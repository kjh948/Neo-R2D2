"""Unit tests for the waypoint -> (power, angle) mapping (CPU, no weights)."""

from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from navstack.waypoints_to_cmd import (  # noqa: E402
    MotionParams,
    sample_waypoints,
    waypoint_to_motion,
)


def straight_chunk(dist_m: float) -> np.ndarray:
    """Cumulative rows: row i is the pose at t=(i+1)*0.1 s of a straight walk."""
    return np.cumsum(np.tile(np.array([[dist_m / 10, 0.0, 0.0]], dtype=np.float32), (10, 1)), axis=0)


class SampleTest(unittest.TestCase):
    def test_interpolation(self):
        wp = np.array([[0.1, 0.0, 0.0], [0.2, 0.0, 0.0]], dtype=np.float32)
        fwd, lat, yaw = sample_waypoints(wp, 0.15)  # halfway between rows 0 and 1
        self.assertAlmostEqual(fwd, 0.15, places=5)
        self.assertAlmostEqual(lat, 0.0, places=5)

    def test_clamps_past_end(self):
        wp = straight_chunk(1.0)
        fwd, _, _ = sample_waypoints(wp, 5.0)
        self.assertAlmostEqual(fwd, 1.0, places=5)

    def test_rejects_bad_shape(self):
        with self.assertRaises(ValueError):
            sample_waypoints(np.zeros((10, 2)), 0.3)


class MotionTest(unittest.TestCase):
    def setUp(self):
        self.p = MotionParams()

    def test_forward_straight(self):
        power, angle = waypoint_to_motion(straight_chunk(1.0), self.p)
        self.assertEqual(angle, 0)
        # 1 m chunk -> ~0.33 m at t=0.333 s -> ~1.0 m/s >> v_full -> capped
        self.assertEqual(power, int(self.p.max_power))

    def test_moderate_forward_speed(self):
        power, angle = waypoint_to_motion(straight_chunk(0.12), self.p)
        self.assertEqual(angle, 0)
        # 0.04 m @0.333 s -> 0.12 m/s -> ~40 % of v_full=0.30
        self.assertTrue(30 <= power <= 50, power)

    def test_left_lateral_positive_angle(self):
        wp = np.tile(np.array([[0.0, 0.1, 0.0]], dtype=np.float32), (10, 1))
        power, angle = waypoint_to_motion(wp, self.p)
        self.assertEqual(angle, 90)   # +lateral = left = +angle (default sign)
        self.assertGreater(power, 0)

    def test_angle_sign_flip(self):
        p = MotionParams(angle_sign=-1.0)
        wp = np.tile(np.array([[0.0, 0.1, 0.0]], dtype=np.float32), (10, 1))
        _, angle = waypoint_to_motion(wp, p)
        self.assertEqual(angle, -90)

    def test_stop_chunk(self):
        power, angle = waypoint_to_motion(np.zeros((10, 3), dtype=np.float32), self.p)
        self.assertEqual((power, angle), (0, 0))

    def test_tiny_motion_below_deadband(self):
        wp = np.tile(np.array([[0.005, 0.001, 0.0]], dtype=np.float32), (10, 1))
        power, angle = waypoint_to_motion(wp, self.p)
        self.assertEqual((power, angle), (0, 0))

    def test_diagonal_45deg(self):
        d = 0.3 / 10
        wp = np.tile(np.array([[d, d, 0.0]], dtype=np.float32), (10, 1))
        _, angle = waypoint_to_motion(wp, self.p)
        self.assertIn(angle, (44, 45, 46))

    def test_reverse_dominant(self):
        wp = np.tile(np.array([[-0.05, 0.0, 0.0]], dtype=np.float32), (10, 1))
        power, angle = waypoint_to_motion(wp, self.p)
        self.assertEqual(angle, 180)
        self.assertGreaterEqual(power, self.p.min_power)

    def test_yaw_weight_biases_left_path(self):
        # pure forward path but chunk curving left (positive yaw): angle > 0
        cum = np.cumsum([[0.02, 0.0, 0.10]] * 10, axis=0, dtype=np.float32)
        power, angle = waypoint_to_motion(cum, MotionParams(yaw_weight=1.0))
        self.assertGreater(angle, self.p.angle_deadband_deg)
        self.assertGreater(power, 0)


if __name__ == "__main__":
    unittest.main()
