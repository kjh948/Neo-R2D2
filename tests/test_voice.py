from __future__ import annotations

import time
import unittest

from support import build_robot, wait_until

from r2d2.voice import VoiceCommand, VoiceRecognizer, VoiceToEventHandler


class _RecordingEvents:
    def __init__(self):
        self.calls = []

    def __getattr__(self, name):
        if name.startswith("_"):
            raise AttributeError(name)

        def record(*args, **kwargs):
            self.calls.append((name, args))

        return record


class CommandMappingTest(unittest.TestCase):
    def setUp(self):
        self.events = _RecordingEvents()
        self.modes = type("M", (), {"wake": lambda self: None})()
        self.handler = VoiceToEventHandler(self.events, self.modes)

    def _fire(self, phrase):
        self.events.calls.clear()
        self.handler.voice_to_event(phrase)
        return self.events.calls

    def test_english_phrases(self):
        cases = {
            "turn left": ("mode", (3,)),
            "rotate right please": ("mode", (4,)),
            "go forward": ("mode", (5,)),
            "shake your head": ("shake_your_head", ()),
            "walk a circle": ("walk_circle", ()),
            "dance now": ("mode", (10,)),
            "lightsaber action": ("lightsaber", ()),
            "move arms": ("arm", ()),
            "patrol": ("patrol", ()),
            "r two d two": ("voice_wake_up", ()),
            "good morning": ("voice_wake_up", ()),
        }
        for phrase, expected in cases.items():
            calls = self._fire(phrase)
            self.assertEqual(calls, [expected], phrase)

    def test_chinese_phrases(self):
        cases = {
            "左 转": ("mode", (3,)),
            "右 转": ("mode", (4,)),
            "看 后面": ("mode", (2,)),
            "向 前 走": ("mode", (5,)),
            "跳 舞": ("mode", (10,)),
            "停止": ("mode_stop", ()),
            "去 巡逻": ("patrol", ()),
            "拿出 激光剑": ("lightsaber", ()),
            "打开 手臂": ("arm", ()),
            "摇 一下 头": ("shake_your_head", ()),
        }
        for phrase, expected in cases.items():
            calls = self._fire(phrase)
            self.assertEqual(calls, [expected], phrase)

    def test_french_phrases_are_the_live_set(self):
        # MainApplication forces Locale("fr"), so the fr arrays are what the
        # shipped robot answers to.
        cases = {
            "bonjour": ("voice_wake_up", ()),
            "salut": ("voice_wake_up", ()),
            "tourne à gauche": ("mode", (3,)),
            "virage à droite": ("mode", (4,)),
            "avance tout droit": ("mode", (5,)),
            "secoue la tête": ("shake_your_head", ()),
            "tourne en rond": ("walk_circle", ()),
            "danse danse": ("mode", (10,)),
            "sabre laser": ("lightsaber", ()),
            "bouge les bras": ("arm", ()),
            "patrouille": ("patrol", ()),
            "qui es-tu": ("mode", (7,)),
        }
        for phrase, expected in cases.items():
            calls = self._fire(phrase)
            self.assertEqual(calls, [expected], phrase)

    def test_commands_with_empty_arrays_are_unreachable_by_voice(self):
        # TURN_AROUND, STOP, MAKE_SOME_NOISE, SKY_WALKER, PRINCESS_LEIA,
        # ANGLE_SECRET and STARK_SECRET have empty R.array entries in every
        # locale, so those phrases fall through to "not recognised" (mode 8).
        for phrase in ("turn around", "stop", "make some sounds", "leia", "angle"):
            self.assertEqual(self._fire(phrase), [("mode", (8,))], phrase)

    def test_unrecognised_phrase_reports_mode_8(self):
        self.assertEqual(self._fire("blah blah"), [("mode", (8,))])

    def test_longest_phrase_wins(self):
        # "arms" and "move arms" both prefix-match "move arms"; the longer,
        # more specific phrase must be the one that resolves.
        self.assertEqual(self._fire("move arms"), [("arm", ())])
        self.assertEqual(self._fire("arms"), [("arm", ())])

    def test_projector_easter_eggs_are_defined_but_inactive(self):
        # SKY_WALKER / PRINCESS_LEIA / ANGLE / STARK have no phrases in the
        # shipped resources, so they can only be reached by extending the table.
        handler = VoiceToEventHandler(self.events, self.modes, phrases={
            VoiceCommand.SKY_WALKER: ["luke"],
            VoiceCommand.PRINCESS_LEIA: ["leia"],
            VoiceCommand.ANGLE_SECRET: ["angle"],
            VoiceCommand.STARK_SECRET: ["stark"],
        })
        self.events.calls.clear()
        handler.voice_to_event("luke skywalker")
        self.assertEqual(self.events.calls, [("mode", (20,))])
        self.events.calls.clear()
        handler.voice_to_event("leia")
        self.assertEqual(self.events.calls, [("mode", (19,))])
        self.events.calls.clear()
        handler.voice_to_event("stark")
        self.assertEqual(self.events.calls, [])

    def test_command_lookup_reports_no_match_for_empty_input(self):
        self.assertIsNone(self.handler.get_command(""))
        self.assertIsNone(self.handler.get_command("nothing like it"))


class RecognizerTest(unittest.TestCase):
    def setUp(self):
        stack = build_robot()
        self.stack = stack
        self.handler = VoiceToEventHandler(stack["events"], None)
        self.recognizer = VoiceRecognizer(self.handler, timeout=0.2)

    def tearDown(self):
        self.recognizer.stop()
        stack = getattr(self, "stack", None)
        if stack:
            stack["events"].close()
            stack["transport"].close()

    def test_start_marks_voice_recognition_mode_for_the_light_ladder(self):
        self.assertTrue(self.recognizer.start())
        self.assertTrue(self.recognizer.is_voice_recognition_mode)
        self.assertFalse(self.recognizer.start(), "a second start is a no-op")

    def test_keyword_reaches_the_handler_and_rearms_the_timer(self):
        # ``turn left`` runs a SoundJob then a 700 ms-delayed ModeJob, so the
        # auto-stop window has to outlast the animation for this assertion.
        self.recognizer.timeout = 3.0
        arms = []
        original = self.recognizer._arm_timeout
        self.recognizer._arm_timeout = lambda: (arms.append(1), original())[1]
        self.recognizer.start()
        self.assertTrue(self.recognizer.feed_keyword("turn left"))
        self.assertTrue(
            wait_until(lambda: '{"cmd":"mode","mode":3}' in self.stack["transport"].raw, timeout=2.0)
        )
        self.assertEqual(len(arms), 2, "start plus one keyword must arm the auto-stop twice")
        self.assertTrue(self.recognizer.is_active)

    def test_it_stops_itself_after_the_timeout(self):
        self.recognizer.start()
        self.assertTrue(wait_until(lambda: not self.recognizer.is_active, timeout=1.5))
        self.assertFalse(self.recognizer.feed_keyword("turn left"), "idle recognizer ignores input")

    def test_wake_up_is_recognised_while_asleep(self):
        self.recognizer.current_mode = 2
        self.recognizer.start()
        self.assertTrue(self.recognizer.feed_keyword("r two d two"))


if __name__ == "__main__":
    unittest.main()
