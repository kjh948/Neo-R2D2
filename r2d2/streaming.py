from __future__ import annotations

import threading
import time
from typing import Callable, Optional

from .log import get_logger
from .ws import WebSocketServer, WebSocketSession

LOG = get_logger("streaming")

# Verified against ``WebSocket/StreamingServer.java``: it is a *websocket*
# server (not HTTP) on port 12121 that allows exactly one viewer and pushes
# binary JPEG frames. A second viewer is rejected with resultCode 421.
STREAM_PORT = 12121
SEND_FPS = 10
FRAME_INTERVAL = 1.0 / SEND_FPS

STREAMING_BUSY = 421


class VideoStreamingServer:
    """Port of ``StreamingServer`` + ``VideoStreamer``.

    Starting a stream stops face detection and pauses the sleep timer, exactly
    as the app does — a viewer watching the camera is user activity, and the
    two features contend for the same sensor frames.
    """

    def __init__(
        self,
        frame_source: Callable[[], Optional[bytes]],
        host: str = "0.0.0.0",
        port: int = STREAM_PORT,
        on_viewer_start: Optional[Callable[[], None]] = None,
        on_viewer_stop: Optional[Callable[[], None]] = None,
        frame_interval: float = FRAME_INTERVAL,
    ) -> None:
        self._frame_source = frame_source
        self.frame_interval = frame_interval
        self.on_viewer_start = on_viewer_start
        self.on_viewer_stop = on_viewer_stop
        self._server = WebSocketServer(
            host=host,
            port=port,
            on_message=self._on_message,
            on_connect=self._on_connect,
            on_disconnect=self._on_disconnect,
        )
        self._viewer: Optional[WebSocketSession] = None
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._pump: Optional[threading.Thread] = None

    def start(self) -> None:
        self._server.start()

    def stop(self) -> None:
        self._stop.set()
        self._server.stop()
        pump, self._pump = self._pump, None
        if pump is not None and pump.is_alive():
            pump.join(timeout=2.0)
        with self._lock:
            self._viewer = None

    @property
    def bound_port(self) -> Optional[int]:
        return self._server.bound_port

    @property
    def has_viewer(self) -> bool:
        with self._lock:
            viewer = self._viewer
        return viewer is not None and not viewer.closed.is_set()

    # -- websocket callbacks --------------------------------------------------
    def _on_connect(self, session: WebSocketSession) -> None:
        with self._lock:
            existing = self._viewer
            if existing is not None and not existing.closed.is_set():
                session.send_text('{"cmd":"streaming","resultCode":421}\n')
                LOG.debug("rejecting second viewer from %s", session.address)
                reject = session
            else:
                self._viewer = session
                reject = None
        if reject is not None:
            reject.close()
            return
        LOG.info("%s entered the streaming room", session.address)
        session.send_text("enter video socket")
        if self.on_viewer_start is not None:
            self.on_viewer_start()
        self._start_pump()

    def _on_disconnect(self, session: WebSocketSession) -> None:
        with self._lock:
            if self._viewer is session:
                self._viewer = None
                stopped = True
            else:
                stopped = False
        if stopped:
            LOG.info("%s left the streaming room", session.address)
            self._stop.set()
            if self.on_viewer_stop is not None:
                self.on_viewer_stop()

    def _on_message(self, session: WebSocketSession, message: str) -> None:
        # The viewer sends nothing meaningful; the app logs it and ignores it.
        LOG.trace("stream viewer said: %r", message[:80])

    # -- frame pump -----------------------------------------------------------
    def _start_pump(self) -> None:
        if self._pump is not None and self._pump.is_alive():
            return
        self._stop.clear()
        self._pump = threading.Thread(target=self._pump_frames, name="video-pump", daemon=True)
        self._pump.start()

    def _pump_frames(self) -> None:
        while not self._stop.is_set():
            with self._lock:
                viewer = self._viewer
            if viewer is None or viewer.closed.is_set():
                return
            frame = self._frame_source()
            if frame:
                if not viewer.send_binary(frame):
                    return
            time.sleep(self.frame_interval)
