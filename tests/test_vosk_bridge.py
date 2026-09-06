import unittest
from pathlib import Path

from r2d2.vosk_bridge import VoskBridge


class _FakeVoiceRecognizer:
    def __init__(self):
        self.started = 0
        self.stopped = 0

    def start(self):
        self.started += 1
        return True

    def stop(self):
        self.stopped += 1


class VoskBridgeTest(unittest.TestCase):
    def test_missing_model_does_not_raise_or_start_recognition(self):
        voice = _FakeVoiceRecognizer()
        bridge = VoskBridge(voice, "/path/that/does/not/exist")

        self.assertFalse(bridge.start())
        self.assertEqual(voice.started, 0)
        bridge.stop()
        self.assertEqual(voice.stopped, 1)

    def test_expands_user_in_model_path(self):
        voice = _FakeVoiceRecognizer()
        bridge = VoskBridge(voice, "~/vosk-model")

        self.assertEqual(bridge.model_path, Path.home() / "vosk-model")

    def test_korean_mode_uses_only_the_korean_model(self):
        voice = _FakeVoiceRecognizer()
        bridge = VoskBridge(voice, "", "~/korean-model", language="ko")

        specs = bridge._model_specs()
        self.assertEqual(len(specs), 1)
        self.assertEqual(specs[0][0], Path.home() / "korean-model")


if __name__ == "__main__":
    unittest.main()
