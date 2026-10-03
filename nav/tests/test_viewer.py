"""Viewer degrades gracefully on headless OpenCV (drive --show safety)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from navstack.frames import encode_jpeg  # noqa: E402
from navstack.viewer import FrameViewer  # noqa: E402


class ViewerTest(unittest.TestCase):
    def test_render_never_raises(self):
        v = FrameViewer("test")
        jpeg = encode_jpeg(np.zeros((40, 40, 3), np.uint8))
        try:
            # headless build: must self-disable with a warning, not crash
            v.render(jpeg, {"raw_text": "x", "cmd": (10, 20)})
            v.render(jpeg, {"waypoints": [[0.1, 0.0, 0.0]] * 5})
        finally:
            v.close()

    def test_disabled_viewer_is_noop(self):
        v = FrameViewer("test")
        v._disabled = True
        v.render(b"not-a-jpeg", {})   # no exception even with garbage input


if __name__ == "__main__":
    unittest.main()
