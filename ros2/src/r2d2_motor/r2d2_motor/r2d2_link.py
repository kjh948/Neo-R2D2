"""Vendored slice of the repo's ``r2d2`` MCU API for ROS 2 use.

Copied from ``r2d2/transport.py`` (``JsonLineTransport``) and ``r2d2/commander.py``
(``Commander``, locomotion subset only) at the time of writing and stripped of the
app-side dependencies (``.log``/``.models``/``.state``) so this ROS package is
self-contained and does not sys.path-hack into the checkout.

If the UART protocol in the ``r2d2`` package changes, sync this file.
"""
from __future__ import annotations

import json
import logging
import os
import select
import threading
import time
from typing import Any, Callable, Dict, Optional

LOG = logging.getLogger("r2d2_link")

LineCallback = Callable[[str], None]

try:  # pragma: no cover - depends on the host image
    import serial  # type: ignore

    _HAS_PYSERIAL = True
except ImportError:  # pragma: no cover
    _HAS_PYSERIAL = False

# ``Commander.java`` cmd constants — exact wire spellings.
CMD_GIN = "gin"
CMD_MOVE = "move"
CMD_READY = "ready"


class JsonLineTransport:
    """Newline-delimited JSON transport with a background reader thread.

    Bytes are accumulated until an ``\\n`` and only plausible JSON objects are
    handed to the callback. Writes are serialised behind a lock so concurrent
    command producers cannot interleave partial lines on the wire.
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

    # -- lifecycle ------------------------------------------------------------
    def open(self) -> None:
        if self._port is not None:
            return
        if self.mock:
            LOG.warning("mock transport enabled: %s (frames are logged, not sent)", self.device)
            self._port = _MockPort()
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
    def send(self, payload: Dict[str, Any]) -> None:
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
    def encode(payload: Dict[str, Any]) -> bytes:
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
        if not raw or raw[0:1] != b"{" or len(raw) <= 2:
            return
        text = raw.decode("utf-8", errors="replace")
        if self._on_line is not None:
            try:
                self._on_line(text)
            except Exception:  # pragma: no cover - handler bugs must not kill the loop
                LOG.exception("uart line handler raised")


class Commander:
    """Locomotion subset of ``r2d2.commander.Commander``.

    Keeps the charging interlock: while the MCU reports a charging state,
    ``move`` frames are dropped (mirrors ``Commander.java``'s
    ``if (getRobotCharging() == 0)`` wrapper). ``state`` only needs a
    ``.charging`` attribute.
    """

    def __init__(self, transport: JsonLineTransport, state: Optional[Any] = None) -> None:
        self.transport = transport
        self.state = state
        self._lock = threading.Lock()

    def _send(self, payload: Dict[str, Any]) -> bool:
        try:
            self.transport.send(payload)
            return True
        except Exception as exc:
            LOG.error("failed to send %s: %s", payload.get("cmd"), exc)
            return False

    @property
    def charging(self) -> int:
        return self.state.charging if self.state is not None else 0

    def software_ready(self) -> bool:
        return self._send({"cmd": CMD_READY})

    def gin(self) -> bool:
        return self._send({"cmd": CMD_GIN})

    def move(self, power: int, angle: int) -> bool:
        if self.charging != 0:
            LOG.debug("move suppressed while charging")
            return False
        with self._lock:  # keep move frames ordered
            return self._send({"cmd": CMD_MOVE, "power": int(power), "angle": int(angle)})


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
    """Loopback port for bench tests without hardware; logs what would be sent."""

    def __init__(self) -> None:
        self._inbox: list = []
        self._pending = bytearray()
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
        LOG.info("uart tx %s", data.decode("utf-8", errors="replace").rstrip("\n"))

    def flush(self) -> None:
        return None

    def inject(self, line: str) -> None:
        self._inbox.append(line if line.endswith("\n") else line + "\n")

    def close(self) -> None:
        self._closed = True
