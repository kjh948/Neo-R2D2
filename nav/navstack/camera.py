"""Frame sources for the navigator: R2D2 video stream or local USB camera (cv2).

Both deliver the latest JPEG bytes; the navigator samples at its own rate.
"""

from __future__ import annotations

import asyncio
import logging
import threading
from typing import Optional

from .r2d2_link import R2D2VideoSource

logger = logging.getLogger("navstack.camera")


class UsbCameraSource:
    """OpenCV USB webcam (Mac host stand-alone runs, and Pi if the camera is
    free -- normally on the robot the R2D2 host owns /dev/video0, so prefer
    R2D2VideoSource there)."""

    def __init__(self, index: int = 0, width: int = 640, height: int = 480,
                 quality: int = 85):
        self.index, self.size, self.quality = index, (width, height), quality
        self.latest: Optional[bytes] = None
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._cap = None

    def open(self) -> bool:
        """Open the device ON THE CALLING (main) thread.

        macOS/AVFoundation cannot run the TCC camera-authorization prompt from a
        non-main thread ("can not spin main run loop from other thread"), and a
        backgrounded process then silently fails isOpened(). Open here, read in
        the worker loop. Linux/V4L2 (Pi5) is indifferent but shares the API.
        """
        import cv2
        self._cap = cv2.VideoCapture(self.index)
        self._cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.size[0])
        self._cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.size[1])
        if not self._cap.isOpened():
            logger.error("cannot open camera %d", self.index)
            self._cap = None
            return False
        return True

    def _run(self, cap) -> None:
        import cv2  # local import: only needed on camera hosts
        while not self._stop.is_set():
            ok, frame = cap.read()
            if not ok:
                self._stop.wait(0.05)
                continue
            ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, self.quality])
            if ok:
                self.latest = buf.tobytes()
            self._stop.wait(0.03)
        cap.release()

    async def connect(self) -> None:
        if not self.open():
            raise RuntimeError(f"USB camera {self.index} unavailable")
        self._thread = threading.Thread(target=self._run, args=(self._cap,),
                                        name="usb-cam", daemon=True)
        self._thread.start()

    async def get_jpeg(self) -> Optional[bytes]:
        return self.latest

    async def close(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2.0)


class StreamCameraSource:
    """JPEG files from a directory (looped) -- bench testing without hardware."""

    def __init__(self, directory: str, fps: float = 4.0):
        import glob
        import os
        self.files = sorted(glob.glob(os.path.join(directory, "*.jpg")) +
                            glob.glob(os.path.join(directory, "*.jpeg")))
        if not self.files:
            raise FileNotFoundError(f"no JPEGs in {directory!r}")
        self.period = 1.0 / max(fps, 0.1)
        self.i = 0

    async def connect(self) -> None:
        pass

    async def get_jpeg(self) -> Optional[bytes]:
        data = open(self.files[self.i % len(self.files)], "rb").read()
        self.i += 1
        await asyncio.sleep(self.period)
        return data

    async def close(self) -> None:
        pass


def make_source(spec: str, robot_host: Optional[str] = None):
    """spec: "r2d2" (robot video stream), "usb[:N]", or "dir:<path>"."""
    if spec == "r2d2":
        if not robot_host:
            raise ValueError("--robot host required for --camera r2d2")
        return R2D2VideoSource(robot_host)
    if spec.startswith("usb"):
        idx = int(spec.split(":", 1)[1]) if ":" in spec else 0
        return UsbCameraSource(idx)
    if spec.startswith("dir:"):
        return StreamCameraSource(spec.split(":", 1)[1])
    raise ValueError(f"unknown camera spec {spec!r} (use r2d2|usb[:N]|dir:PATH)")
