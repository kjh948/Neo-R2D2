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
CMD_ARM = "arm"
CMD_DEBUG = "debug"
CMD_GIN = "gin"
CMD_LCD = "lcd"
CMD_LED = "led"
CMD_LIGHTSABER = "lightsaber"
CMD_MODE = "mode"
CMD_MOVE = "move"
CMD_MOVE_HEAD_ANGLE = "head-angle"
CMD_MOVE_HEAD_DIR = "head-dir"
CMD_MOVE_HEAD_SHIFT = "head-shift"
CMD_PLAY_SOUND = "play_sound"
CMD_POWER_OFF = "shut-down"
CMD_PROJECTOR = "projector"
CMD_READY = "ready"
CMD_RESET = "reset-wdt"
CMD_SET_HEAD_DIR_POWER = "d-head-power"
CMD_SET_LEG_POWER = "d-leg-power"

# ``ModeJob.prohabittedModeWhileCharging`` — typo in the original, kept as the
# canonical name (same set as r2d2/commander.py:35).
MODES_PROHIBITED_WHILE_CHARGING = frozenset({1, 2, 3, 4, 5, 9, 10, 12, 15})

# Sentinel for led/lcd channels: key omitted from the frame = "leave alone".
UNCHANGE = -1


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
        mock_responds: bool = True,
    ) -> None:
        self.device = device
        self.baudrate = baudrate
        self.read_timeout = read_timeout
        self.mock = mock
        self.mock_responds = mock_responds
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
            LOG.warning("mock transport enabled: %s (frames are logged, not sent; canned MCU %s)",
                        self.device, "replies on" if self.mock_responds else "replies off")
            self._port = _MockPort(responds=self.mock_responds)
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
    """Host -> MCU command surface, synced from ``r2d2/commander.py``.

    Mirrors ``Commander.java`` including its charging interlocks: while the MCU
    reports a charging state the locomotion/head commands are dropped silently
    (``if (getRobotCharging() == 0)`` wrapper) and the animated modes in
    ``MODES_PROHIBITED_WHILE_CHARGING`` are refused. ``send``-style helpers
    return ``False`` on a write failure so callers can tell the frame did not
    leave the box. ``state`` only needs a ``.charging`` attribute.
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

    def send_raw(self, text: str) -> bool:
        """Write a non-JSON frame (``resetMCU`` pokes the MCU with ``''``)."""
        try:
            self.transport.send(text)
            return True
        except Exception as exc:
            LOG.error("failed to send raw frame %r: %s", text, exc)
            return False

    @property
    def charging(self) -> int:
        return self.state.charging if self.state is not None else 0

    def _motion_allowed(self) -> bool:
        return self.charging == 0

    # -- lifecycle / status ---------------------------------------------------
    def software_ready(self) -> bool:
        return self._send({"cmd": CMD_READY})

    def gin(self) -> bool:
        return self._send({"cmd": CMD_GIN})

    def reset(self) -> bool:
        return self._send({"cmd": CMD_RESET})

    def debug(self) -> bool:
        return self._send({"cmd": CMD_DEBUG})

    def power_off(self) -> bool:
        return self._send({"cmd": CMD_POWER_OFF})

    def reset_mcu(self) -> bool:
        return self.send_raw("''")

    # -- locomotion -----------------------------------------------------------
    def move(self, power: int, angle: int) -> bool:
        if not self._motion_allowed():
            LOG.debug("move suppressed while charging")
            return False
        with self._lock:  # keep move frames ordered
            return self._send({"cmd": CMD_MOVE, "power": int(power), "angle": int(angle)})

    def move_head_angle(self, angle: int) -> bool:
        if not self._motion_allowed():
            return False
        return self._send({"cmd": CMD_MOVE_HEAD_ANGLE, "angle": int(angle)})

    def head_shift(self, angle: int) -> bool:
        if not self._motion_allowed():
            return False
        return self._send({"cmd": CMD_MOVE_HEAD_SHIFT, "angle": int(angle)})

    def move_head_direction(self, direction: int) -> bool:
        if not self._motion_allowed():
            return False
        return self._send({"cmd": CMD_MOVE_HEAD_DIR, "dir": int(direction)})

    def change_head_power(self, power: int) -> bool:
        return self._send({"cmd": CMD_SET_HEAD_DIR_POWER, "power": int(power)})

    def change_leg_power(self, power: int) -> bool:
        return self._send({"cmd": CMD_SET_LEG_POWER, "power": int(power)})

    # -- modes ----------------------------------------------------------------
    def mode(self, mode: int) -> bool:
        mode = int(mode)
        if self.charging != 0 and mode in MODES_PROHIBITED_WHILE_CHARGING:
            LOG.info("mode %d refused while charging", mode)
            return False
        return self._send({"cmd": CMD_MODE, "mode": mode})

    # -- accessories ----------------------------------------------------------
    def projector_mode(self, mode: int) -> bool:
        return self._send({"cmd": CMD_PROJECTOR, "mode": int(mode)})

    def extend_arm(self, power: int) -> bool:
        return self._send({"cmd": CMD_ARM, "power": int(power)})

    def lightsaber(self, power: int) -> bool:
        return self._send({"cmd": CMD_LIGHTSABER, "power": int(power)})

    def led(self, r: int = UNCHANGE, b: int = UNCHANGE, y: int = UNCHANGE, g: int = UNCHANGE) -> bool:
        # Insertion order matches the Java (r, b, y, g) so byte-level frame
        # diffs against a capture of the original app still line up.
        payload: Dict[str, Any] = {"cmd": CMD_LED}
        for key, value in (("r", r), ("b", b), ("y", y), ("g", g)):
            if value != UNCHANGE:
                payload[key] = int(value)
        return self._send(payload)

    def lcd(self, s: int = UNCHANGE, l: int = UNCHANGE) -> bool:
        payload: Dict[str, Any] = {"cmd": CMD_LCD}
        for key, value in (("s", s), ("l", l)):
            if value != UNCHANGE:
                payload[key] = int(value)
        return self._send(payload)

    def play_sound(self, sound_id: int, interrupt: int = 1) -> bool:
        """Ask the MCU to play a clip (the app plays locally; the frame exists)."""
        return self._send(
            {"cmd": CMD_PLAY_SOUND, "sound_id": int(sound_id), "interrupt": int(interrupt)})


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
    """Loopback port for bench tests without hardware; logs what would be sent
    and answers ``ready``/``gin`` with canned MCU frames so the whole
    receive -> event-queue -> /r2d2/status pipeline can be exercised offline
    (``mock_responds`` off = write-log only, the original behaviour)."""

    _CANNED_GIN = ('{"cmd":"gin","batt":87,"charging-status":0,"arm":0,'
                   '"lightsaber":0,"projector":0,"lcd_s":1,"lcd_l":1,'
                   '"mode":0,"head":0,"error":"NO ERROR"}')

    def __init__(self, responds: bool = True) -> None:
        self._inbox: list = []
        self._pending = bytearray()
        self._closed = False
        self._responds = responds

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
        LOG.info("uart tx %s", text)
        if not self._responds:
            return
        if '"cmd":"ready"' in text:
            self.inject('{"cmd":"ready"}')
        elif '"cmd":"gin"' in text:
            self.inject(self._CANNED_GIN)

    def flush(self) -> None:
        return None

    def inject(self, line: str) -> None:
        self._inbox.append(line if line.endswith("\n") else line + "\n")

    def close(self) -> None:
        self._closed = True
