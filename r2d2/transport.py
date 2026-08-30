from __future__ import annotations

import json
import os
import select
import threading
import time
from typing import Any, Callable, Dict, Optional, Union

from .log import get_logger

LOG = get_logger("transport")

LineCallback = Callable[[str], None]

try:  # pragma: no cover - depends on the host image
    import serial  # type: ignore

    _HAS_PYSERIAL = True
except ImportError:  # pragma: no cover
    _HAS_PYSERIAL = False


class JsonLineTransport:
    """Newline-delimited JSON transport with a background reader thread.

    Mirrors ``SerialPortService``: bytes are accumulated until an ``\\n`` and
    only plausible JSON objects are handed to the callback. Writes are
    serialised behind a lock so concurrent command producers cannot interleave
    partial lines on the wire.
    """

    def __init__(
        self,
        device: str,
        baudrate: int = 115200,
        read_timeout: float = 1.0,
        on_line: Optional[LineCallback] = None,
        mock: bool = False,
    ) -> None:
        self.device = device
        self.baudrate = baudrate
        self.read_timeout = read_timeout
        self.mock = mock
        self._on_line = on_line
        self._write_lock = threading.Lock()
        self._stop = threading.Event()
        self._reader: Optional[threading.Thread] = None
        self._port: Any = None
        self._buffer = bytearray()
        self._echo = False

    # -- lifecycle ------------------------------------------------------------
    def open(self) -> None:
        if self._port is not None:
            return
        if self.mock:
            LOG.warning("mock transport enabled: %s (frames are logged, not sent)", self.device)
            self._port = _MockPort(self)
        elif _HAS_PYSERIAL:
            self._port = serial.Serial(  # type: ignore[attr-defined]
                port=self.device,
                baudrate=self.baudrate,
                timeout=self.read_timeout,
                write_timeout=self.read_timeout,
                bytesize=serial.EIGHTBITS,  # type: ignore[attr-defined]
                parity=serial.PARITY_NONE,  # type: ignore[attr-defined]
                stopbits=serial.STOPBITS_ONE,  # type: ignore[attr-defined]
            )
        else:
            self._port = _PosixSerial.open(self.device, self.baudrate, self.read_timeout)
        self._stop.clear()
        self._reader = threading.Thread(target=self._read_loop, name="uart-reader", daemon=True)
        self._reader.start()

    def close(self) -> None:
        self._stop.set()
        reader, self._reader = self._reader, None
        if reader is not None and reader.is_alive():
            reader.join(timeout=self.read_timeout + 1.0)
        port, self._port = self._port, None
        if port is not None:
            try:
                port.close()
            except Exception:  # pragma: no cover - best effort teardown
                pass

    @property
    def is_open(self) -> bool:
        return self._port is not None

    # -- outgoing -------------------------------------------------------------
    def send(self, payload: Union[Dict[str, Any], str]) -> None:
        if self._port is None:
            raise RuntimeError(f"transport {self.device} is not open")
        data = self.encode(payload)
        with self._write_lock:
            try:
                self._port.write(data)
                flush = getattr(self._port, "flush", None)
                if flush is not None:
                    flush()
            except Exception as exc:
                LOG.error("UART write failed: %s", exc)
                raise

    @staticmethod
    def encode(payload: Union[Dict[str, Any], str]) -> bytes:
        if isinstance(payload, str):
            text = payload
        else:
            text = json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
        return (text.rstrip("\n") + "\n").encode("utf-8")

    # -- incoming -------------------------------------------------------------
    def _read_loop(self) -> None:
        pending = bytearray()
        while not self._stop.is_set():
            try:
                chunk = self._port.read(1)
            except Exception as exc:
                if self._stop.is_set():
                    break
                LOG.error("UART read failed: %s", exc)
                time.sleep(0.5)
                continue
            if not chunk:
                continue
            for byte in chunk:
                if byte == 0x0A:
                    line = bytes(pending)
                    pending.clear()
                    self._dispatch(line)
                elif byte == 0x0D:
                    continue
                else:
                    pending.append(byte)
                    if len(pending) > 4096:
                        LOG.warning("dropping over-long UART line (%d bytes)", len(pending))
                        pending.clear()

    def _dispatch(self, raw: bytes) -> None:
        if not raw:
            return
        # SerialPortService only forwards payloads that look like JSON objects.
        if raw[0:1] != b"{" or len(raw) <= 2:
            LOG.trace("ignoring non-JSON uart line: %r", raw[:80])
            return
        text = raw.decode("utf-8", errors="replace")
        if self._on_line is not None:
            try:
                self._on_line(text)
            except Exception:  # pragma: no cover - handler bugs must not kill the loop
                LOG.exception("uart line handler raised")

    def feed_from_mock(self, text: str) -> None:
        """Inject a received line while running against the mock transport."""
        for line in text.splitlines():
            self._dispatch(line.strip().encode("utf-8"))

    def set_echo(self, enabled: bool) -> None:
        self._echo = enabled


class _PosixSerial:
    """Minimal raw-serial fallback for hosts without pyserial."""

    def __init__(self, fd: int) -> None:
        import fcntl
        import termios

        self._fd = fd
        self._file = os.fdopen(fd, "r+b", buffering=0)
        flags = fcntl.fcntl(fd, fcntl.F_GETFL)
        fcntl.fcntl(fd, fcntl.F_SETFL, flags & ~os.O_NONBLOCK)
        attrs = termios.tcgetattr(fd)
        attrs[0] = termios.IGNPAR
        attrs[1] = 0
        attrs[2] = termios.CS8 | termios.CREAD | termios.CLOCAL
        attrs[3] = 0
        attrs[4] = termios.B115200
        attrs[5] = termios.B115200
        attrs[6][termios.VMIN] = 0
        attrs[6][termios.VTIME] = 10
        termios.tcsetattr(fd, termios.TCSANOW, attrs)

    @classmethod
    def open(cls, device: str, baudrate: int, timeout: float) -> "_PosixSerial":
        fd = os.open(device, os.O_RDWR | os.O_NOCTTY | os.O_NONBLOCK)
        try:
            return cls(fd)
        except Exception:
            os.close(fd)
            raise

    def read(self, size: int) -> bytes:
        ready, _, _ = select.select([self._fd], [], [], 1.0)
        if not ready:
            return b""
        try:
            return os.read(self._fd, size)
        except OSError:
            return b""

    def write(self, data: bytes) -> None:
        os.write(self._fd, data)

    def flush(self) -> None:
        return None

    def close(self) -> None:
        try:
            self._file.close()
        except Exception:  # pragma: no cover
            pass


class _MockPort:
    """Loopback port used by ``--mock-serial`` and the unit tests.

    Serves at most the number of bytes requested, so the reader loop is
    exercised exactly as it is against a real device rather than getting whole
    lines handed to it.
    """

    def __init__(self, transport: JsonLineTransport) -> None:
        self._transport = transport
        self._inbox: list[str] = []
        self._pending = bytearray()
        self.sent: list[str] = []
        self._closed = False

    def read(self, size: int) -> bytes:
        if self._closed:
            return b""
        while not self._pending and self._inbox:
            self._pending.extend(self._inbox.pop(0).encode("utf-8"))
        if not self._pending:
            time.sleep(0.01)
            return b""
        if size <= 0:
            return b""
        chunk = bytes(self._pending[:size])
        del self._pending[:size]
        return chunk

    def write(self, data: bytes) -> None:
        text = data.decode("utf-8", errors="replace").rstrip("\n")
        self.sent.append(text)
        LOG.trace("uart tx %s", text)

    def flush(self) -> None:
        return None

    def inject(self, line: str) -> None:
        self._inbox.append(line if line.endswith("\n") else line + "\n")

    def close(self) -> None:
        self._closed = True
