from __future__ import annotations

import shutil
import subprocess
from typing import Optional

from .log import get_logger
from .vision import CameraWorker, FaceDetector

LOG = get_logger("central")

# ``CentralController.setMute`` restores 70 % of the music stream.
UNMUTED_VOLUME_RATIO = 0.7


class CentralController:
    """Port of ``CentralController`` — camera, face detection and voice owner.

    ``startFaceDetection``/``startVoiceRecognition`` refuse to run in patrol
    (4) and pair (3) mode: patrol needs the MCU's own sensors, and pair mode
    owns the camera for the QR code.
    """

    def __init__(self, state, mode_controller, detector: FaceDetector, camera: CameraWorker) -> None:
        self.state = state
        self.mode_controller = mode_controller
        self.detector = detector
        self.camera = camera
        self.voice = None  # wired by the app once a recognizer exists
        self.streaming = False

    # -- face detection -------------------------------------------------------
    def start_face_detection(self, force: bool = False) -> bool:
        if not self.state.face_detection:
            LOG.debug("start face detection when disabled")
            return False
        mode = self.mode_controller.get_mode() if self.mode_controller is not None else 1
        if not force and mode in (4, 3):
            LOG.debug("cannot start face detection in mode %d", mode)
            return False
        if not self.detector.init():
            return False
        self.detector.set_enabled(True)
        self.camera.start()
        return True

    def stop_face_detection(self, force: bool = False) -> None:
        self.detector.set_enabled(False)

    def is_face_detected(self) -> bool:
        return bool(self.detector.enabled and self.detector.is_face_detected)

    # -- qr code --------------------------------------------------------------
    def start_qr_reader(self) -> None:
        if not self.detector.init():
            return
        self.detector.enabled = True
        self.camera.start()

    def stop_qr_reader(self) -> None:
        return None

    # -- camera ---------------------------------------------------------------
    def start_camera(self) -> bool:
        return self.camera.start()

    def stop_camera(self) -> None:
        self.camera.stop()

    # -- voice ----------------------------------------------------------------
    def start_voice_recognition(self) -> bool:
        if not self.state.voice_recognition:
            LOG.debug("cannot start voice recognition when disabled")
            return False
        mode = self.mode_controller.get_mode() if self.mode_controller is not None else 1
        if mode in (4, 3):
            LOG.debug("cannot start voice recognition in mode %d", mode)
            return False
        if self.voice is None:
            return False
        return self.voice.start()

    def stop_voice_recognition(self) -> None:
        if self.voice is not None:
            self.voice.stop()

    # -- audio ----------------------------------------------------------------
    def set_mute(self, muted: bool) -> None:
        """``AudioManager.setRingerMode`` + 70 % music volume, via amixer/pactl."""
        if muted:
            self._mixer_mute()
            return
        self._mixer_unmute()

    def _run(self, args) -> Optional[str]:
        binary = shutil.which(args[0])
        if binary is None:
            return None
        try:
            completed = subprocess.run(args, capture_output=True, text=True, timeout=5.0, check=False)
        except (OSError, subprocess.SubprocessError) as exc:
            LOG.debug("%s failed: %s", " ".join(args), exc)
            return None
        return completed.stdout if completed.returncode == 0 else None

    def _mixer_mute(self) -> None:
        if self._run(["pactl", "set-sink-mute", "@DEFAULT@", "1"]) is None:
            self._run(["amixer", "-q", "set", "Master", "mute"])

    def _mixer_unmute(self) -> None:
        if self._run(["pactl", "set-sink-mute", "@DEFAULT@", "0"]) is None:
            self._run(["amixer", "-q", "set", "Master", "unmute"])
        if self._run(["pactl", "set-sink-volume", "@DEFAULT@", "70%"]) is None:
            self._run(["amixer", "-q", "set", "Master", "70%"])

    # -- video streaming ------------------------------------------------------
    def start_video_streaming(self) -> None:
        self.streaming = True
        self.camera.start()

    def stop_video_streaming(self) -> None:
        self.streaming = False

    def stop_all_control(self) -> None:
        self.stop_camera()
        self.stop_voice_recognition()
