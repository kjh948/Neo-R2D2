from __future__ import annotations

import base64
import hashlib
import socket
import struct
import threading
from typing import Callable, Dict, Iterable, Optional

from .log import get_logger

LOG = get_logger("ws")

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

OP_CONT = 0x0
OP_TEXT = 0x1
OP_BINARY = 0x2
OP_CLOSE = 0x8
OP_PING = 0x9
OP_PONG = 0xA

MessageHandler = Callable[["WebSocketSession", str], None]
ConnectHandler = Callable[["WebSocketSession"], None]


class ProtocolError(Exception):
    pass


class WebSocketSession:
    """A single accepted websocket connection."""

    def __init__(self, conn: socket.socket, address) -> None:
        self.conn = conn
        self.address = address
        self.uuid: Optional[str] = None
        self.device_name: str = "unknown"
        self.closed = threading.Event()
        self._send_lock = threading.Lock()

    def send_text(self, text: str) -> bool:
        if self.closed.is_set():
            return False
        try:
            self.conn.sendall(_encode_frame(text.encode("utf-8"), OP_TEXT))
            return True
        except OSError as exc:
            LOG.debug("send to %s failed: %s", self.address, exc)
            self.closed.set()
            return False

    def send_binary(self, payload: bytes) -> bool:
        if self.closed.is_set():
            return False
        try:
            self.conn.sendall(_encode_frame(payload, OP_BINARY))
            return True
        except OSError as exc:
            LOG.debug("binary send to %s failed: %s", self.address, exc)
            self.closed.set()
            return False

    def close(self) -> None:
        if self.closed.is_set():
            return
        self.closed.set()
        try:
            self.conn.sendall(_encode_frame(b"", OP_CLOSE))
        except OSError:
            pass
        try:
            self.conn.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            self.conn.close()
        except OSError:
            pass

    def __str__(self) -> str:
        return f"<ws {self.address} uuid={self.uuid} name={self.device_name}>"


class WebSocketServer:
    """Threaded RFC6455 server covering the subset the robot protocol needs."""

    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 8887,
        path: str = "/",
        on_message: Optional[MessageHandler] = None,
        on_connect: Optional[ConnectHandler] = None,
        on_disconnect: Optional[ConnectHandler] = None,
    ) -> None:
        self.host = host
        self.port = port
        self.path = path
        self._on_message = on_message
        self._on_connect = on_connect
        self._on_disconnect = on_disconnect
        self._listeners: list[socket.socket] = []
        self._sessions: set[WebSocketSession] = set()
        self._sessions_lock = threading.Lock()
        self._stop = threading.Event()
        self._accept_threads: list[threading.Thread] = []

    # -- lifecycle ------------------------------------------------------------
    def start(self) -> None:
        """Bind synchronously, then accept in the background.

        Binding in the caller keeps ``bound_port`` valid the moment ``start()``
        returns, so a supervisor can probe readiness instead of sleeping.
        """
        self._stop.clear()
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            listener.bind((self.host, self.port))
        except OSError as exc:
            LOG.error("cannot bind websocket server on %s:%d: %s", self.host, self.port, exc)
            raise
        listener.listen(16)
        listener.settimeout(1.0)
        self._listeners.append(listener)
        LOG.info("websocket server listening on %s:%d%s", self.host, self.bound_port, self.path)
        thread = threading.Thread(target=self._accept_loop, args=(listener,), name="ws-server", daemon=True)
        self._accept_threads.append(thread)
        thread.start()

    def stop(self) -> None:
        self._stop.set()
        for listener in self._listeners:
            try:
                listener.close()
            except OSError:
                pass
        self._listeners.clear()
        with self._sessions_lock:
            sessions = list(self._sessions)
        for session in sessions:
            session.close()

    @property
    def bound_port(self) -> Optional[int]:
        for listener in self._listeners:
            try:
                return listener.getsockname()[1]
            except OSError:
                continue
        return None

    def _accept_loop(self, listener: socket.socket) -> None:
        while not self._stop.is_set():
            try:
                conn, address = listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            worker = threading.Thread(
                target=self._handle_connection,
                args=(conn, address),
                name=f"ws-{address[0]}:{address[1]}",
                daemon=True,
            )
            worker.start()

    # -- per connection -------------------------------------------------------
    def _handle_connection(self, conn: socket.socket, address) -> None:
        session: Optional[WebSocketSession] = None
        try:
            conn.settimeout(120.0)
            request = _read_http_request(conn)
            if request is None:
                conn.close()
                return
            key = request.headers.get("sec-websocket-key")
            if not key:
                _send_http(conn, 400, "bad request: missing Sec-WebSocket-Key")
                conn.close()
                return
            if self.path not in (request.path, request.path.split("?")[0]):
                LOG.debug("rejecting path %r", request.path)
            accept = base64.b64encode(hashlib.sha1((key + GUID).encode("ascii")).digest()).decode("ascii")
            conn.sendall(
                (
                    "HTTP/1.1 101 Switching Protocols\r\n"
                    "Upgrade: websocket\r\n"
                    "Connection: Upgrade\r\n"
                    f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
                ).encode("ascii")
            )
            session = WebSocketSession(conn, address)
            with self._sessions_lock:
                self._sessions.add(session)
            if self._on_connect is not None:
                try:
                    self._on_connect(session)
                except Exception:  # pragma: no cover
                    LOG.exception("connect handler raised")
            self._read_loop(session)
        except Exception as exc:  # pragma: no cover - defensive
            LOG.debug("connection %s failed: %s", address, exc)
        finally:
            if session is not None:
                with self._sessions_lock:
                    self._sessions.discard(session)
                if self._on_disconnect is not None:
                    try:
                        self._on_disconnect(session)
                    except Exception:  # pragma: no cover
                        LOG.exception("disconnect handler raised")
                session.close()
            else:
                try:
                    conn.close()
                except OSError:
                    pass

    def _read_loop(self, session: WebSocketSession) -> None:
        while not self._stop.is_set() and not session.closed.is_set():
            try:
                opcode, payload = _read_frame(session.conn)
            except (ProtocolError, OSError, socket.timeout) as exc:
                LOG.debug("connection %s closed: %s", session.address, exc)
                return
            if opcode == OP_CLOSE:
                return
            if opcode == OP_PING:
                session.conn.sendall(_encode_frame(payload, OP_PONG))
                continue
            if opcode in (OP_TEXT, OP_BINARY):
                text = payload.decode("utf-8", errors="replace")
                if self._on_message is None:
                    continue
                for line in _split_json_lines(text):
                    try:
                        self._on_message(session, line)
                    except Exception:  # pragma: no cover
                        LOG.exception("message handler raised for %s", session)

    # -- fan-out --------------------------------------------------------------
    def broadcast(self, text: str, exclude: Optional[Iterable[WebSocketSession]] = None) -> int:
        skip = set(exclude or ())
        with self._sessions_lock:
            targets = [s for s in self._sessions if s not in skip]
        sent = 0
        for session in targets:
            if session.send_text(text):
                sent += 1
        return sent

    @property
    def session_count(self) -> int:
        with self._sessions_lock:
            return len(self._sessions)


class _HttpRequest:
    def __init__(self, path: str, headers: Dict[str, str]) -> None:
        self.path = path
        self.headers = headers


def _read_http_request(conn: socket.socket) -> Optional[_HttpRequest]:
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = conn.recv(1024)
        if not chunk:
            return None
        data += chunk
        if len(data) > 16384:
            return None
    header_blob, _remainder = data.split(b"\r\n\r\n", 1)
    lines = header_blob.decode("latin1").split("\r\n")
    if not lines:
        return None
    parts = lines[0].split(" ")
    path = parts[1] if len(parts) > 1 else "/"
    headers: Dict[str, str] = {}
    for line in lines[1:]:
        if ":" not in line:
            continue
        name, _, value = line.partition(":")
        headers[name.strip().lower()] = value.strip()
    return _HttpRequest(path, headers)


def _send_http(conn: socket.socket, status: int, message: str) -> None:
    body = message.encode("utf-8")
    conn.sendall(
        f"HTTP/1.1 {status} {message}\r\nContent-Length: {len(body)}\r\n"
        "Content-Type: text/plain\r\nConnection: close\r\n\r\n".encode("ascii") + body
    )


def _read_exact(conn: socket.socket, length: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < length:
        chunk = conn.recv(length - len(chunks))
        if not chunk:
            raise ProtocolError("connection closed mid-frame")
        chunks.extend(chunk)
    return bytes(chunks)


def _read_frame(conn: socket.socket) -> tuple[int, bytes]:
    header = _read_exact(conn, 2)
    opcode = header[0] & 0x0F
    masked = bool(header[1] & 0x80)
    length = header[1] & 0x7F
    if length == 126:
        length = int.from_bytes(_read_exact(conn, 2), "big")
    elif length == 127:
        length = int.from_bytes(_read_exact(conn, 8), "big")
    if length > 4 * 1024 * 1024:
        raise ProtocolError(f"frame too large: {length}")
    mask_key = _read_exact(conn, 4) if masked else b""
    payload = _read_exact(conn, length) if length else b""
    if masked:
        payload = bytes(byte ^ mask_key[i % 4] for i, byte in enumerate(payload))
    # Clients may fragment a message; reassemble continuation frames.
    while not header[0] & 0x80:
        nxt = _read_exact(conn, 2)
        nxt_len = nxt[1] & 0x7F
        if nxt_len == 126:
            nxt_len = int.from_bytes(_read_exact(conn, 2), "big")
        elif nxt_len == 127:
            nxt_len = int.from_bytes(_read_exact(conn, 8), "big")
        nxt_mask = bool(nxt[1] & 0x80)
        key = _read_exact(conn, 4) if nxt_mask else b""
        part = _read_exact(conn, nxt_len) if nxt_len else b""
        if nxt_mask:
            part = bytes(byte ^ key[i % 4] for i, byte in enumerate(part))
        payload += part
    return opcode, payload


def _encode_frame(payload: bytes, opcode: int) -> bytes:
    header = bytearray([0x80 | opcode])
    length = len(payload)
    if length < 126:
        header.append(length)
    elif length < 65536:
        header.append(126)
        header.extend(struct.pack(">H", length))
    else:
        header.append(127)
        header.extend(struct.pack(">Q", length))
    return bytes(header) + payload


def _split_json_lines(text: str) -> list[str]:
    """The web client sometimes pipelines several JSON objects in one frame."""
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if len(lines) == 1 and not lines[0].startswith("{"):
        return []
    if all(line.startswith("{") for line in lines):
        return lines
    stripped = text.strip()
    return [stripped] if stripped.startswith("{") else []
