from __future__ import annotations

import unittest

from support import build_robot, wait_until

# The queue-building helpers are exercised by capturing what ``_begin`` is
# handed, so structural tests do not have to wait out 600 ms animation steps.


class QueueStructureTest(unittest.TestCase):
    def setUp(self):
        stack = build_robot()
        self.events = stack["events"]
        self.captured = []
        self._original = self.events._begin
        self.events._begin = lambda jobs: self.captured.append(list(jobs))

    def tearDown(self):
        self.events._begin = self._original
        self.events.close()

    def _jobs(self):
        self.assertEqual(len(self.captured), 1)
        return self.captured[0]

    def test_shake_head_sequence(self):
        self.events.shake_your_head()
        jobs = self._jobs()
        self.assertEqual([type(j).__name__ for j in jobs],
                         ["SoundJob"] + ["MoveHeadJob"] * 5)
        self.assertEqual([j.angle for j in jobs[1:]], [-45, 45, -45, 45, 0])
        self.assertEqual([j.delay for j in jobs], [0, 600, 600, 600, 600, 600])

    def test_turn_left_uses_sound_then_mode_after_700ms(self):
        self.events.turn_left()
        jobs = self._jobs()
        self.assertEqual([(type(j).__name__, j.delay) for j in jobs],
                         [("SoundJob", 0), ("ModeJob", 700)])
        self.assertEqual(jobs[1].mode, 3)

    def test_dance_is_four_cycles_of_ten_jobs(self):
        self.events.dance()
        jobs = self._jobs()
        self.assertEqual(len(jobs), 40)
        kinds = [type(j).__name__ for j in jobs]
        self.assertEqual(kinds.count("SoundJob"), 4)
        self.assertEqual(kinds.count("ModeJob"), 16)
        self.assertEqual(kinds.count("MoveHeadJob"), 20)

    def test_lightsaber_and_arm_prefix_projector_off_and_chirp(self):
        self.events.lightsaber(1)
        self.assertEqual([type(j).__name__ for j in self._jobs()],
                         ["ProjectorJob", "SoundJob", "LightsaberJob"])

    def test_short_lcd_inverts_state_defaults(self):
        stack_state = self.events.state
        stack_state.short_lcd = False
        stack_state.long_lcd = True
        self.events.short_lcd()
        jobs = self._jobs()
        lcd = jobs[-1]
        # s=2 opens the short panel; l=2 because the long one was open.
        self.assertEqual((lcd.s, lcd.l), (2, 2))

    def test_patrol_toggle_reads_mode_before_stopjob(self):
        self.events.patrol()
        jobs = self._jobs()
        self.assertEqual([type(j).__name__ for j in jobs],
                         ["ProjectorJob", "SoundJob", "PatrolJob"])
        self.assertTrue(jobs[-1].enable)


class QueueExecutionTest(unittest.TestCase):
    def setUp(self):
        stack = build_robot()
        self.stack = stack
        self.events = stack["events"]
        self.transport = stack["transport"]

    def tearDown(self):
        self.events.close()
        self.transport.close()

    def test_every_action_stops_the_mcu_animation_first(self):
        self.transport.clear()
        self.events.turn_left()
        self.assertTrue(wait_until(lambda: self.transport.raw))
        self.assertEqual(self.transport.raw[0], '{"cmd":"mode","mode":0}')

    def test_delay_belongs_to_the_job_that_follows_it(self):
        from r2d2.events import MoveHeadJob, SoundJob

        self.transport.clear()
        self.events._begin([MoveHeadJob(11, 0), MoveHeadJob(22, 150), MoveHeadJob(33, 0)])
        self.assertTrue(wait_until(lambda: len(self.transport.raw) >= 2))
        first_two = self.transport.raw[:2]
        self.assertEqual(first_two, ['{"cmd":"mode","mode":0}', '{"cmd":"head-angle","angle":11}'])
        # The third frame must not have overtaken the delayed second one.
        self.assertNotIn('{"cmd":"head-angle","angle":33}', self.transport.raw[:2])
        self.assertTrue(wait_until(lambda: len(self.transport.raw) >= 3))
        self.assertEqual(self.transport.raw[2], '{"cmd":"head-angle","angle":22}')

    def test_a_replacement_queue_abandons_the_pending_one(self):
        from r2d2.events import MoveHeadJob

        self.transport.clear()
        self.events._begin([MoveHeadJob(11, 0), MoveHeadJob(99, 400)])
        self.assertTrue(wait_until(lambda: len(self.transport.raw) >= 2))
        self.events._begin([MoveHeadJob(22, 0)])
        self.assertTrue(wait_until(lambda: '{"cmd":"head-angle","angle":22}' in self.transport.raw))
        self.assertNotIn('{"cmd":"head-angle","angle":99}', self.transport.raw)

    def test_mode_dispatcher_routes_to_behaviours(self):
        # 8 and 11 and unknown modes all land on notRecognize.
        calls = []
        self.events.not_recognize = lambda: calls.append("not_recognize")  # type: ignore[assignment]
        for mode in (8, 11, 999):
            self.events.mode(mode)
            self.assertEqual(calls[-1], "not_recognize")

    def test_projector_off_pauses_audio(self):
        self.transport.clear()
        self.events.projector_mode(0)
        self.assertTrue(wait_until(lambda: '{"cmd":"projector","mode":0}' in self.transport.raw))

    def test_reset_mcu_writes_the_literal_quote_pair(self):
        self.transport.clear()
        self.events.reset_mcu()
        self.assertEqual(self.transport.raw[-1], "''")


class SoundMappingTest(unittest.TestCase):
    def test_unknown_sound_ids_fall_back_to_the_default_clip(self):
        from r2d2.sound import sound_name_for_id

        self.assertEqual(sound_name_for_id(7), "happiness_confirmation")
        self.assertEqual(sound_name_for_id(100), "starwar01_right_02")
        self.assertEqual(sound_name_for_id(302), "stark")
        self.assertEqual(sound_name_for_id(4242), "happy_three_chirp")

    def test_every_id_resolves_to_a_shipped_file(self):
        import os
        from r2d2.sound import SOUND_EFFECTS

        missing = [name for name in SOUND_EFFECTS.values()
                   if not os.path.isfile(os.path.join("sound_effects", name + ".mp3"))]
        self.assertEqual(missing, [])


if __name__ == "__main__":
    unittest.main()
