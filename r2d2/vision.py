from __future__ import annotations

import os
import threading
import time
from typing import Callable, List, Optional, Tuple

from .log import get_logger
from .sound import STARTLED_THREE_TONE, SoundPlayer

LOG = get_logger("vision")

try:  # pragma: no cover - optional dependency
    import cv2  # type: ignore
    import numpy as np  # type: ignore

    _HAS_CV2 = True
except ImportError:  # pragma: no cover
    cv2 = None  # type: ignore
    np = None  # type: ignore
    _HAS_CV2 = False

# ``FaceDetection.java`` tunables, verbatim.
SCALE_DOWN_RATIO = 3
RELATIVE_FACE_SIZE = 0.2
DETECT_SCALE_FACTOR = 1.1
DETECT_MIN_NEIGHBORS = 5
DETECT_FLAGS = 2
MAX_STORED_FACES = 10
FACE_EXPIRE_MS = 1500.0
TARGET_ACQUIRE_MS = 1500.0
TRACKING_TIMEOUT_MS = 1500.0
MATCH_DISTANCE_RATIO = 0.35
MATCH_AREA_RATIO = 0.7
FOLLOW_SPEED = 0.5
MAX_DEGREE = 40.0
MAX_SHIFT_ANGLE = 5
# ``FaceDetection.initFaceDetectionLibrary`` opens ``R.raw.haarcascade_frontalface_alt``
# but writes it to a temp file *named* ``lbpcascade_frontalface.xml``. The name
# lies: the bytes (and therefore the detector actually in use) are the Haar
# "stump-based 20x20 gentle adaboost" cascade, not an LBP one.
ROTATE_DEGREES = 270.0
RECT_COLOR = (0, 255, 0)
RECT_THICKNESS = 3

DEFAULT_CASCADE = "haarcascade_frontalface_alt.xml"


class Face:
    """Port of ``FaceDetection/Face``: a detection plus its lifetime bookkeeping."""

    __slots__ = ("face_id", "rect", "first_exist_time", "last_exist_time")

    def __init__(self, face_id: int, rect, now: float) -> None:
        self.face_id = face_id
        self.rect = rect
        self.first_exist_time = now
        self.last_exist_time = now

    @property
    def center(self) -> Tuple[float, float]:
        x, y, w, h = self.rect
        return (x + w / 2.0, y + h / 2.0)

    @property
    def area(self) -> int:
        _, _, w, h = self.rect
        return int(w) * int(h)


class FaceDetector:
    """Port of ``FaceDetection`` — Haar/LBP cascade tracking + head follow.

    Runs on a frame callback instead of OpenCV's Android camera view. The
    firmware owns the actual pan motor, so the follow controller emits
    ``head-shift`` with a clamped +/-5 degree nudging value rather than an
    absolute angle, which is what makes the head creep onto the face instead of
    snapping to it.
    """

    def __init__(
        self,
        commander,
        sound_player: Optional[SoundPlayer] = None,
        lights=None,
        cascade_dir: str = "",
        cascade_name: str = DEFAULT_CASCADE,
        rotate: bool = False,
        on_face_detected: Optional[Callable[[], None]] = None,
    ) -> None:
        self.commander = commander
        self.sound_player = sound_player
        self.lights = lights
        self.on_face_detected = on_face_detected
        self.rotate = rotate
        self.cascade_path = self._locate_cascade(cascade_dir, cascade_name)
        self.detector = None
        self.enabled = False
        self.is_face_detected = False
        self.is_face_tracking = False
        self.stored_faces: List[Face] = []
        self.target_face: Optional[Face] = None
        self.show_target_face = False
        self.width = 640
        self.height = 480
        self._absolute_face_size = 0
        self._face_id = 0
        self._in_flight = False
        self._tracking_timer: Optional[threading.Timer] = None
        self._lock = threading.RLock()
        self.last_frame: Optional[bytes] = None

    @staticmethod
    def _locate_cascade(directory: str, name: str) -> Optional[str]:
        candidates = []
        if directory:
            candidates.append(os.path.join(directory, name))
        candidates.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", name))
        for candidate in candidates:
            if os.path.isfile(candidate):
                return candidate
        LOG.warning("cascade %s not found; face detection disabled", name)
        return None

    def init(self) -> bool:
        if not _HAS_CV2:
            LOG.warning("opencv not installed; face detection disabled")
            return False
        if self.detector is not None:
            return True
        if self.cascade_path is None:
            return False
        detector = cv2.CascadeClassifier(self.cascade_path)
        if detector.empty():
            LOG.error("cascade classifier failed to load %s", self.cascade_path)
            return False
        self.detector = detector
        LOG.info("loaded cascade classifier from %s", self.cascade_path)
        return True

    def set_enabled(self, enabled: bool) -> None:
        self.enabled = bool(enabled)
        if not enabled:
            self.on_face_lose()

    # -- per frame ------------------------------------------------------------
    def process_frame(self, frame) -> Optional[object]:
        """Run one detection pass. Returns the annotated gray frame."""
        if not self.enabled or self.detector is None or frame is None:
            return frame
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY) if frame.ndim == 3 else frame
        if self.rotate:
            center = (gray.shape[1] / 2.0, gray.shape[0] / 2.0)
            matrix = cv2.getRotationMatrix2D(center, ROTATE_DEGREES, 1.0)
            gray = cv2.warpAffine(gray, matrix, (gray.shape[1], gray.shape[0]))
        self.width, self.height = int(gray.shape[1]), int(gray.shape[0])
        self.last_frame = gray

        small = cv2.resize(
            gray,
            (max(1, self.width // SCALE_DOWN_RATIO), max(1, self.height // SCALE_DOWN_RATIO)),
        )
        if self._absolute_face_size == 0:
            computed = round(small.shape[0] * RELATIVE_FACE_SIZE)
            if computed > 0:
                self._absolute_face_size = int(computed)

        with self._lock:
            if self._in_flight:
                return gray
            self._in_flight = True
        try:
            detections = self.detector.detectMultiScale(
                small,
                scaleFactor=DETECT_SCALE_FACTOR,
                minNeighbors=DETECT_MIN_NEIGHBORS,
                flags=DETECT_FLAGS,
                minSize=(self._absolute_face_size, self._absolute_face_size),
            )
        finally:
            with self._lock:
                self._in_flight = False

        rects = [(x * SCALE_DOWN_RATIO, y * SCALE_DOWN_RATIO, w * SCALE_DOWN_RATIO, h * SCALE_DOWN_RATIO)
                 for (x, y, w, h) in (detections if len(detections) else [])]
        self.update_face(rects)

        if self.show_target_face and self.target_face is not None and frame.ndim == 3:
            x, y, w, h = (int(v) for v in self.target_face.rect)
            cv2.rectangle(frame, (x, y), (x + w, y + h), RECT_COLOR, RECT_THICKNESS)
        return frame

    # -- tracking -------------------------------------------------------------
    def update_face(self, rects: List[Tuple[int, int, int, int]]) -> None:
        now = time.monotonic()
        target_exists = False
        with self._lock:
            for rect in rects:
                found = self._search_prev_face(rect)
                if found is None:
                    if len(self.stored_faces) < MAX_STORED_FACES:
                        self._face_id += 1
                        self.stored_faces.append(Face(self._face_id, rect, now))
                else:
                    found.last_exist_time = now
                    found.rect = rect
                    if self.target_face is not None and found.face_id == self.target_face.face_id:
                        target_exists = True

            self.stored_faces = [f for f in self.stored_faces if now - f.last_exist_time <= FACE_EXPIRE_MS / 1000.0]

            if not self.stored_faces:
                self.target_face = None
            elif now - self.stored_faces[0].first_exist_time >= TARGET_ACQUIRE_MS / 1000.0:
                self.target_face = self.stored_faces[0]
            else:
                self.target_face = None

            self.show_target_face = target_exists

        if self.target_face is None:
            self.on_face_lose()
        else:
            self.on_face_detected_event()
            self.change_head_direction()

    def _search_prev_face(self, rect) -> Optional[Face]:
        limit = self.width * MATCH_DISTANCE_RATIO
        x, y, w, h = rect
        area = max(1, int(w) * int(h))
        for existing in self.stored_faces:
            ex, ey, ew, eh = existing.rect
            delta = ((x - ex) ** 2 + (y - ey) ** 2) ** 0.5
            other = max(1, int(ew) * int(eh))
            size_delta = min(area, other) / max(area, other)
            if delta < limit and size_delta > MATCH_AREA_RATIO:
                return existing
        return None

    def on_face_detected_event(self) -> None:
        self.is_face_detected = True
        if not self.is_face_tracking:
            self._start_tracking()
        with self._lock:
            if self._tracking_timer is not None:
                self._tracking_timer.cancel()
            timer = threading.Timer(TRACKING_TIMEOUT_MS / 1000.0, self._tracking_expired)
            timer.daemon = True
            self._tracking_timer = timer
            timer.start()
        if self.on_face_detected is not None:
            self.on_face_detected()

    def _tracking_expired(self) -> None:
        if not self.is_face_detected:
            self._stop_tracking()

    def _start_tracking(self) -> None:
        self.is_face_tracking = True
        if self.lights is not None:
            self.lights.face_detect_light_start()
        if self.sound_player is not None:
            self.sound_player.play_id(STARTLED_THREE_TONE, False)

    def _stop_tracking(self) -> None:
        self.is_face_tracking = False
        if self.lights is not None:
            self.lights.face_detect_light_stop()

    def on_face_lose(self) -> None:
        self.is_face_detected = False

    # -- head follow ----------------------------------------------------------
    def change_head_direction(self) -> None:
        if self.target_face is None:
            return
        face_x = self.target_face.center[0]
        center_x = self.width / 2.0
        target_angle = ((face_x - center_x) / float(self.width)) * MAX_DEGREE
        rotate_angle = int(FOLLOW_SPEED * target_angle)
        if (target_angle > 2.0 or target_angle < -2.0) and rotate_angle != 0:
            rotate_angle = max(-MAX_SHIFT_ANGLE, min(MAX_SHIFT_ANGLE, rotate_angle))
            self.commander.head_shift(int(rotate_angle))

    def close(self) -> None:
        with self._lock:
            if self._tracking_timer is not None:
                self._tracking_timer.cancel()
                self._tracking_timer = None


class CameraWorker:
    """Frames source replacing ``JavaCameraView`` (640x480, OpenCV backend)."""

    def __init__(
        self,
        index: int = 0,
        width: int = 640,
        height: int = 480,
        mock: bool = False,
    ) -> None:
        self.index = index
        self.width = width
        self.height = height
        self.mock = mock
        self._capture = None
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self.running = False
        self.on_frame: Optional[Callable[[object], None]] = None
        self.most_recent: Optional[bytes] = None

    def start(self) -> bool:
        if self.running:
            return True
        if not _HAS_CV2:
            LOG.warning("opencv not installed; camera disabled")
            return False
        if self._capture is None:
            self._capture = cv2.VideoCapture(self.index)
            if not self._capture.isOpened():
                LOG.error("cannot open camera %s", self.index)
                self._capture = None
                return False
            self._capture.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
            self._capture.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)
        self._stop.clear()
        self.running = True
        self._thread = threading.Thread(target=self._loop, name="camera", daemon=True)
        self._thread.start()
        return True

    def stop(self) -> None:
        self._stop.set()
        self.running = False
        thread, self._thread = self._thread, None
        if thread is not None and thread.is_alive():
            thread.join(timeout=2.0)

    def release(self) -> None:
        self.stop()
        if self._capture is not None:
            try:
                self._capture.release()
            except Exception:  # pragma: no cover
                pass
            self._capture = None

    def _loop(self) -> None:
        assert self._capture is not None
        while not self._stop.is_set():
            ok, frame = self._capture.read()
            if not ok or frame is None:
                LOG.debug("camera frame unavailable")
                time.sleep(0.05)
                continue
            if self.on_frame is not None:
                try:
                    annotated = self.on_frame(frame)
                except Exception:  # pragma: no cover
                    LOG.exception("frame handler raised")
                    annotated = None
                source = annotated if annotated is not None else frame
                try:
                    ok2, encoded = cv2.imencode(".jpg", source, [int(cv2.IMWRITE_JPEG_QUALITY), 70])
                    if ok2:
                        self.most_recent = encoded.tobytes()
                except Exception:  # pragma: no cover
                    LOG.debug("jpeg encode failed", exc_info=True)
            time.sleep(0.001)
