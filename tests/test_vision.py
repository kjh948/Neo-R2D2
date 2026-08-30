from __future__ import annotations

import time
import unittest


from r2d2.vision import (
    FACE_EXPIRE_MS,
    MAX_SHIFT_ANGLE,
    TARGET_ACQUIRE_MS,
    FaceDetector,
)


class _StubCommander:
    def __init__(self):
        self.shifts = []

    def head_shift(self, angle):
        self.shifts.append(angle)
        return True


class TrackingTest(unittest.TestCase):
    def setUp(self):
        self.commander = _StubCommander()
        self.detector = FaceDetector(self.commander, cascade_dir="sound_effects")
        self.detector.width = 640
        self.detector.height = 480
        self.detector.enabled = True

    def _detect(self, rects):
        self.detector.update_face(rects)

    def test_a_new_face_is_not_the_target_until_it_persists(self):
        self._detect([(100, 100, 80, 80)])
        self.assertIsNone(self.detector.target_face, "a first sighting must not become the target")
        self.assertFalse(self.detector.is_face_detected)
        # Age it past the 1.5 s acquisition window.
        self.detector.stored_faces[0].first_exist_time -= TARGET_ACQUIRE_MS / 1000.0 + 0.1
        self._detect([(100, 100, 80, 80)])
        self.assertIsNotNone(self.detector.target_face)
        self.assertTrue(self.detector.is_face_detected)

    def test_a_moving_face_keeps_its_identity(self):
        self._detect([(100, 100, 80, 80)])
        first_id = self.detector.stored_faces[0].face_id
        self.detector.stored_faces[0].first_exist_time -= 2.0
        self._detect([(140, 110, 82, 78)])
        self.assertEqual(len(self.detector.stored_faces), 1)
        self.assertEqual(self.detector.stored_faces[0].face_id, first_id)

    def test_a_far_face_is_a_new_identity(self):
        self._detect([(50, 50, 80, 80)])
        self._detect([(400, 300, 80, 80)])
        self.assertEqual(len(self.detector.stored_faces), 2)

    def test_a_similarly_placed_face_of_another_size_is_a_new_identity(self):
        self._detect([(100, 100, 60, 60)])
        # Same top-left, but the area ratio falls under the 0.7 gate.
        self._detect([(100, 100, 200, 200)])
        self.assertEqual(len(self.detector.stored_faces), 2)

    def test_faces_expire_after_their_last_sighting(self):
        self._detect([(100, 100, 80, 80)])
        self.detector.stored_faces[0].last_exist_time -= FACE_EXPIRE_MS / 1000.0 + 0.1
        self._detect([])
        self.assertEqual(self.detector.stored_faces, [])
        self.assertIsNone(self.detector.target_face)
        self.assertFalse(self.detector.is_face_detected)

    def test_the_face_bank_is_capped(self):
        for index in range(30):
            self._detect([(index * 40 % 600, 10, 30, 30)])
        self.assertLessEqual(len(self.detector.stored_faces), 10)


class HeadFollowTest(unittest.TestCase):
    def setUp(self):
        self.commander = _StubCommander()
        self.detector = FaceDetector(self.commander, cascade_dir="sound_effects")
        self.detector.width = 640

    def _target_at(self, x, width=80):
        from r2d2.vision import Face

        self.detector.target_face = Face(1, (x, 100, width, 80), time.monotonic())

    def test_deadzone_in_the_middle_does_not_move_the_head(self):
        # Face centre must sit within 32 px of the frame centre for the derived
        # angle to stay under the 2 degree gate (640/40 * 2 = 32).
        self._target_at(292)  # centre x = 332, offset 12 px -> 0.75 deg
        self.detector.change_head_direction()
        self.assertEqual(self.commander.shifts, [])
        # Just outside the gate it nudges by a single degree.
        self._target_at(330)  # centre x = 370, offset 50 px -> 3.1 deg -> 1
        self.detector.change_head_direction()
        self.assertEqual(self.commander.shifts, [1])

    def test_a_face_on_the_right_shifts_right(self):
        self._target_at(560)
        self.detector.change_head_direction()
        self.assertEqual(len(self.commander.shifts), 1)
        self.assertGreater(self.commander.shifts[0], 0)

    def test_the_command_is_clamped_to_five_degrees(self):
        self._target_at(600)
        self.detector.change_head_direction()
        self.assertTrue(all(-MAX_SHIFT_ANGLE <= s <= MAX_SHIFT_ANGLE for s in self.commander.shifts),
                        self.commander.shifts)
        self.assertEqual(self.commander.shifts, [MAX_SHIFT_ANGLE])

    def test_negative_side_mirrors(self):
        self._target_at(-40)
        self.detector.change_head_direction()
        self.assertEqual(self.commander.shifts, [-MAX_SHIFT_ANGLE])


class CascadeTest(unittest.TestCase):
    def test_the_cascade_the_app_actually_loads_is_the_haar_alt_one(self):
        # FaceDetection writes R.raw.haarcascade_frontalface_alt into a temp
        # file *named* lbpcascade_frontalface.xml; the bytes are the Haar
        # "stump-based 20x20 gentle adaboost" detector, not an LBP one.
        detector = FaceDetector(_StubCommander(), cascade_dir="sound_effects")
        self.assertTrue(detector.cascade_path.endswith("haarcascade_frontalface_alt.xml"))

    def test_the_lbp_alternatives_are_still_selectable(self):
        from r2d2.vision import DEFAULT_CASCADE
        import os

        self.assertEqual(DEFAULT_CASCADE, "haarcascade_frontalface_alt.xml")
        detector = FaceDetector(_StubCommander(), cascade_dir="sound_effects",
                                cascade_name="lbpcascade_frontalface_improved.xml")
        self.assertTrue(os.path.isfile(detector.cascade_path))

    def test_missing_cascade_disables_detection_rather_than_raising(self):
        detector = FaceDetector(_StubCommander(), cascade_dir="/nonexistent", cascade_name="nope.xml")
        self.assertIsNone(detector.cascade_path)
        self.assertFalse(detector.init())


if __name__ == "__main__":
    unittest.main()
