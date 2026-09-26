"""Optional live camera window with inference overlay (drive --show).

Requires a GUI-capable OpenCV build (opencv-python, NOT opencv-python-headless).
The window must be pumped from the main thread -- the navigator's asyncio loop
runs there, so render() is called from the sampler coroutine right after each
prediction. All failures degrade to a one-time warning; --show never kills the
navigation loop.
"""

from __future__ import annotations

from typing import Dict, List, Optional

import numpy as np

from .frames import decode_jpeg

GUI_INSTALL_HINT = ("pip uninstall -y opencv-python-headless && "
                    "pip install opencv-python")


class FrameViewer:
    def __init__(self, title: str = "navstack"):
        self.title = title
        self._cv2 = None
        self._disabled = False

    def _ensure(self):
        if self._cv2 is not None or self._disabled:
            return self._cv2
        try:
            import cv2
            probe = getattr(cv2, "namedWindow", None)
            if probe is None:
                raise ImportError("headless build")
            cv2.namedWindow(self.title, cv2.WINDOW_NORMAL | cv2.WINDOW_KEEPRATIO)
            cv2.resizeWindow(self.title, 640, 520)
            self._cv2 = cv2
        except Exception as exc:
            self._disabled = True
            import logging
            logging.getLogger("navstack.viewer").warning(
                "--show disabled: %s (install the GUI build: %s)", exc, GUI_INSTALL_HINT)
        return self._cv2

    def render(self, jpeg: bytes, info: Dict[str, object]) -> None:
        cv2 = self._ensure()
        if cv2 is None:
            return
        try:
            img = cv2.cvtColor(decode_jpeg(jpeg), cv2.COLOR_RGB2BGR)
        except Exception:
            return
        img = self._banner(img, info)
        if (wps := info.get("waypoints")) is not None:
            img = self._minimap(img, np.asarray(wps))
        cv2.imshow(self.title, img)
        if cv2.waitKey(1) & 0xFF in (ord("q"), 27):   # q/Esc closes gracefully
            self._disabled = True

    def close(self) -> None:
        if self._cv2 is not None:
            try:
                self._cv2.destroyAllWindows()
            except Exception:
                pass
            self._cv2 = None

    # ---- drawing --------------------------------------------------------
    @staticmethod
    def _banner(img, info: Dict[str, object]):
        cv2 = FrameViewer._require()
        lines: List[str] = []
        if info.get("raw_text"):
            lines.append(str(info["raw_text"]))
        cmd = info.get("cmd")
        if cmd is not None:
            lines.append(f"cmd power={cmd[0]} angle={cmd[1]}")
        extra = []
        for key in ("stop", "seq", "latency_ms", "subgoal_node", "subgoal_dist_m"):
            if key in info and info[key] is not None:
                extra.append(f"{key}={info[key]}")
        if extra:
            lines.append(" ".join(str(e) for e in extra))
        h = 24 * len(lines) + 10
        overlay = img.copy()
        cv2.rectangle(overlay, (0, 0), (img.shape[1], h), (0, 0, 0), -1)
        img = cv2.addWeighted(overlay, 0.55, img, 0.45, 0)
        for i, text in enumerate(lines):
            cv2.putText(img, text[:110], (8, 20 + i * 24),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1, cv2.LINE_AA)
        return img

    @staticmethod
    def _minimap(img, wps):
        """Bird's-eye of the predicted chunk: x=forward up, y=lateral left."""
        cv2 = FrameViewer._require()
        size, pad = 140, 10
        x0 = img.shape[1] - size - pad
        y0 = img.shape[0] - size - pad
        cv2.rectangle(img, (x0, y0), (x0 + size, y0 + size), (0, 0, 0), -1)
        cv2.rectangle(img, (x0, y0), (x0 + size, y0 + size), (90, 90, 90), 1)
        cx, cy = x0 + size // 2, y0 + size          # robot at bottom-centre
        scale = (size * 0.85) / max(float(np.abs(wps[:, :2]).max()), 0.5)
        pts = [(int(cx + w * scale), int(cy - f * scale)) for f, w, _ in wps]
        prev = (cx, cy)
        for p in pts:
            cv2.line(img, prev, p, (80, 220, 80), 2)
            prev = p
        if pts:
            cv2.circle(img, pts[-1], 4, (60, 60, 255), -1)
        cv2.putText(img, "path", (x0 + 6, y0 + 16),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.45, (180, 180, 180), 1, cv2.LINE_AA)
        return img

    @staticmethod
    def _require():
        import cv2
        return cv2
