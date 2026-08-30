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
    """The three-state listening machine from ``VoiceRecognizer.java``.

    ``start()`` lands in MODE_WAIT, where only a wake word does anything; a wake
    word opens a 15 s MODE_WAKE window in which every phrase executes.
    """

    def setUp(self):
        stack = build_robot()
        self.stack = stack
        self.handler = VoiceToEventHandler(stack["events"], None)
        self.recognizer = VoiceRecognizer(self.handler, timeout=0.2)
        self.restored = []
        stack["events"].restore_light = lambda: self.restored.append("restore")
        stack["events"].end_voice = lambda: self.restored.append("end_voice")

    def tearDown(self):
        self.recognizer.stop()
        self.stack["events"].close()
        self.stack["transport"].close()

    def test_start_enters_wait_without_claiming_a_voice_session(self):
        self.assertEqual(self.recognizer.current_mode, VoiceRecognizer.MODE_DISABLE, "initial state")
        self.assertTrue(self.recognizer.start())
        self.assertEqual(self.recognizer.current_mode, VoiceRecognizer.MODE_WAIT)
        # isVoiceRecognitionMode (which drives the back-LED 202) only turns on
        # once a phrase actually lands.
        self.assertFalse(self.recognizer.is_voice_recognition_mode)
        self.assertFalse(self.recognizer.start(), "a second start is refused")

    def test_non_wake_phrase_is_ignored_while_waiting(self):
        self.recognizer.start()
        self.stack["transport"].clear()
        self.assertFalse(self.recognizer.feed_keyword("turn left"))
        self.assertEqual(self.recognizer.current_mode, VoiceRecognizer.MODE_WAIT)
        time.sleep(0.3)
        self.assertEqual(self.stack["transport"].raw, [], "the robot must not move")

    def test_wake_word_opens_the_window_for_other_commands(self):
        self.recognizer.timeout = 3.0
        self.recognizer.start()
        self.assertTrue(self.recognizer.feed_keyword("r two d two"))
        self.assertEqual(self.recognizer.current_mode, VoiceRecognizer.MODE_WAKE)
        self.assertTrue(self.recognizer.is_voice_recognition_mode)
        self.stack["transport"].clear()
        self.assertTrue(self.recognizer.feed_keyword("turn left"))
        self.assertTrue(
            wait_until(lambda: '{"cmd":"mode","mode":3}' in self.stack["transport"].raw, timeout=2.0)
        )

    def test_every_phrase_inside_the_window_reopens_it(self):
        self.recognizer.timeout = 0.4
        self.recognizer.start()
        self.recognizer.feed_keyword("bonjour")
        first_deadline = self.recognizer._timer
        time.sleep(0.25)
        self.recognizer.feed_keyword("patrol")
        self.assertIsNotNone(self.recognizer._timer)
        self.assertIsNot(self.recognizer._timer, first_deadline, "the 15 s window must restart")
        self.assertEqual(self.recognizer.current_mode, VoiceRecognizer.MODE_WAKE)

    def test_the_window_lapses_back_to_wait_without_stopping(self):
        self.recognizer.start()
        self.recognizer.feed_keyword("bonjour")
        self.assertTrue(wait_until(lambda: self.recognizer.current_mode == VoiceRecognizer.MODE_WAIT,
                                   timeout=2.0))
        self.assertTrue(self.recognizer.is_active, "it keeps listening; only sleep/patrol/pair stop it")
        self.assertFalse(self.recognizer.is_voice_recognition_mode)
        self.assertIn("end_voice", self.restored, "endVoiceEvent() restores the lights")

    def test_stop_disables_and_restores_the_lights(self):
        self.recognizer.start()
        self.recognizer.feed_keyword("bonjour")
        self.recognizer.stop()
        self.assertEqual(self.recognizer.current_mode, VoiceRecognizer.MODE_DISABLE)
        self.assertFalse(self.recognizer.is_active)
        self.assertIn("restore", self.restored)
        self.assertFalse(self.recognizer.feed_keyword("bonjour"), "disabled recognizer drops input")


if __name__ == "__main__":
    unittest.main()
