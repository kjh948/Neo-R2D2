from __future__ import annotations

import json
import mimetypes
import os
import posixpath
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Callable, Optional, Tuple
from urllib.parse import unquote, urlsplit

from .log import get_logger

LOG = get_logger("web")

WEB_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")
DEFAULT_PORT = 8080
INDEX = "index.html"


class _Handler(BaseHTTPRequestHandler):
    server_version = "r2d2-console/1.0"
    protocol_version = "HTTP/1.1"

    # Filled in by WebConsoleServer via the class attributes below so the
    # handler stays stateless across requests.
    root: str = WEB_ROOT
    status_provider: Optional[Callable[[], dict]] = None

    def log_message(self, fmt, *args):  # noqa: D102 - route to our logger
        LOG.trace("%s %s", self.address_string(), fmt % args)

    def do_GET(self) -> None:  # noqa: N802
        parts = urlsplit(self.path)
        path = unquote(parts.path)

        if path == "/health":
            return self._json({"ok": True, **(self.status_provider() if self.status_provider else {})})
        if path in ("/", ""):
            path = "/" + INDEX

        body, ctype, status = self._read_static(path)
        if body is None:
            self.send_response(404)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        # The console is edited on the host and reloaded on the pad; a cached
        # copy would silently run stale protocol code.
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self) -> None:  # noqa: N802
        self.do_GET()

    def _json(self, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _read_static(self, path: str) -> Tuple[Optional[bytes], str, int]:
        # normalise, then refuse anything that escapes the web root.
        rel = posixpath.normpath(path.lstrip("/"))
        if rel.startswith("..") or os.path.isabs(rel) or not rel:
            LOG.warning("rejected path traversal attempt: %r", path)
            return None, "", 403
        candidate = os.path.realpath(os.path.join(self.root, rel))
        if not candidate.startswith(os.path.realpath(self.root) + os.sep) and candidate != os.path.realpath(self.root):
            return None, "", 403
        if os.path.isdir(candidate):
            candidate = os.path.join(candidate, INDEX)
        if not os.path.isfile(candidate):
            return None, "", 404
        ctype, _ = mimetypes.guess_type(candidate)
        if ctype is None:
            ctype = "application/octet-stream"
        if ctype.startswith("text/") or ctype in ("application/javascript", "application/json"):
            ctype += "; charset=utf-8"
        try:
            with open(candidate, "rb") as handle:
                return handle.read(), ctype, 200
        except OSError as exc:  # pragma: no cover
            LOG.error("cannot read %s: %s", candidate, exc)
            return None, "", 500


class WebConsoleServer:
    """Serves the browser console that drives the robot over WebSocket.

    The page itself speaks only the two WebSocket ports the Android app exposed
    (8887 commands, 12121 video); this server exists so an operator can just
    open ``http://<robot>:8080/`` on the LAN. It ships no build step and loads
    nothing from a CDN, because the robot's access point has no uplink.
    """

    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = DEFAULT_PORT,
        root: str = WEB_ROOT,
        status_provider: Optional[Callable[[], dict]] = None,
    ) -> None:
        self.host = host
        self.port = port
        # ``staticmethod`` keeps a plain callable: assigning a bare function as
        # a class attribute would bind it as a method and pass ``self`` to it.
        handler = type("BoundHandler", (_Handler,),
                       {"root": root, "status_provider": staticmethod(status_provider) if status_provider else None})
        self._httpd = ThreadingHTTPServer((host, port), handler)
        self._httpd.daemon_threads = True
        self._thread: Optional[threading.Thread] = None

    def start(self) -> None:
        if self._thread is not None:
            return
        self._thread = threading.Thread(target=self._httpd.serve_forever, name="web-console", daemon=True)
        self._thread.start()
        LOG.info("web console on http://%s:%d/", self.host, self.bound_port)

    def stop(self) -> None:
        self._httpd.shutdown()
        self._httpd.server_close()
        thread, self._thread = self._thread, None
        if thread is not None and thread.is_alive():
            thread.join(timeout=2.0)

    @property
    def bound_port(self) -> int:
        return self._httpd.server_address[1]
