from __future__ import annotations

import base64
import json
import os
import socket
import sys
import time
from typing import Any, Dict, List, Optional

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import logging  # noqa: E402

# Keep unittest output readable; set R2D2_TEST_LOG=debug to see r2d2 logging.
logging.getLogger("r2d2").setLevel(
    {"critical": logging.CRITICAL, "error": logging.ERROR, "debug": logging.DEBUG,
     "info": logging.INFO}.get(os.environ.get("R2D2_TEST_LOG", ""), logging.CRITICAL)
)

from r2d2.commander import Commander  # noqa: E402
from r2d2.config import Config  # noqa: E402
from r2d2.leds import LEDLightController  # noqa: E402
from r2d2.sound import SoundPlayer  # noqa: E402
from r2d2.state import RobotState  # noqa: E402
from r2d2.transport import JsonLineTransport  # noqa: E402


class RecordingTransport(JsonLineTransport):
    """Transport that keeps every encoded frame so tests can assert on the wire."""

    def __init__(self, on_line=None) -> None:
        super().__init__("/dev/recording", mock=True, on_line=on_line)
        self.open()

    @property
    def frames(self) -> List[Dict[str, Any]]:
        out = []
        for text in self._port.sent:
            try:
                out.append(json.loads(text))
            except ValueError:
                out.append({"_raw": text})
        return out

    @property
    def raw(self) -> List[str]:
        return list(self._port.sent)

    def clear(self) -> None:
        self._port.sent.clear()

    def cmds(self) -> List[str]:
        return [f.get("cmd") for f in self.frames]


def build_robot(with_modes: bool = False, sleep_time: float = 180.0, patrol_time: float = 60.0,
                pair_timeout: float = 30.0, **overrides):
    """A fully wired stack with mock sound/serial and no network listeners.

    ``with_modes`` adds the behaviour state machine (whose timers are shrunk by
    the keyword args so a test can watch a sleep/patrol/pair transition happen).
    """
    from r2d2.events import EventHandler
    from r2d2.mcu_commands import McuCommandReceiver, McuHooks

    config = Config()
    config.state_file = None
    for key, value in overrides.items():
        setattr(config, key, value)
    transport = RecordingTransport()
    state = RobotState(None)
    state.name = "R2-D2"
    state.udid = "robot-uuid"
    commander = Commander(transport, state)
    sound = SoundPlayer(config.sound_dir, mock=True)
    lights = LEDLightController(commander, state)
    events = EventHandler(commander=commander, sound_player=sound, led_controller=lights, state=state)
    events.start()
    stack = {
        "config": config,
        "transport": transport,
        "state": state,
        "commander": commander,
        "sound": sound,
        "lights": lights,
        "events": events,
    }
    if with_modes:
        from r2d2.modes import ModeController

        modes = ModeController(
            events=events,
            lights=lights,
            sleep_time=sleep_time,
            patrol_time=patrol_time,
            pair_timeout=pair_timeout,
        )
        events.mode_controller = modes
        lights.ctx.mode = modes.get_mode
        stack["modes"] = modes
        stack["mcu"] = McuCommandReceiver(
            event_handler=events,
            state=state,
            lights=lights,
            mode_controller=modes,
            hooks=McuHooks(notify=lambda: None),
        )
    else:
        stack["mcu"] = McuCommandReceiver(
            event_handler=events,
            state=state,
            lights=lights,
            hooks=McuHooks(notify=lambda: None),
        )
    return stack


def wait_until(predicate, timeout: float = 3.0, interval: float = 0.02) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return predicate()


class WsClient:
    """Tiny RFC6455 client: the tests need the same framing the web console uses."""

    def __init__(self, port: int, host: str = "127.0.0.1") -> None:
        self.socket = socket.create_connection((host, port), timeout=5)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        self.socket.sendall(
            (
                f"GET / HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\n"
                f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n"
                "Sec-WebSocket-Version: 13\r\n\r\n"
            ).encode("ascii")
        )
        blob = b""
        while b"\r\n\r\n" not in blob:
            chunk = self.socket.recv(1024)
            if not chunk:
                raise ConnectionError("handshake failed")
            blob += chunk
        if b"101" not in blob:
            raise ConnectionError(blob.decode("latin1"))
        self._pending = blob.split(b"\r\n\r\n", 1)[1]

    def send(self, payload: Dict[str, Any]) -> None:
        data = payload if isinstance(payload, str) else json.dumps(payload)
        raw = data.encode("utf-8")
        mask = os.urandom(4)
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(raw))
        length = len(raw)
        if length < 126:
            header = bytes([0x81, 0x80 | length])
        else:
            header = bytes([0x81, 0x80 | 126]) + length.to_bytes(2, "big")
        self.socket.sendall(header + mask + masked)

    def send_raw(self, text: str) -> None:
        self.send(text)

    def _recv_exact(self, n: int) -> bytes:
        while len(self._pending) < n:
            chunk = self.socket.recv(4096)
            if not chunk:
                raise ConnectionError("closed")
            self._pending += chunk
        head, self._pending = self._pending[:n], self._pending[n:]
        return head

    def recv_frame(self) -> Optional[str]:
        try:
            header = self._recv_exact(2)
        except (ConnectionError, socket.timeout):
            return None
        opcode = header[0] & 0x0F
        length = header[1] & 0x7F
        if length == 126:
            length = int.from_bytes(self._recv_exact(2), "big")
        elif length == 127:
            length = int.from_bytes(self._recv_exact(8), "big")
        payload = self._recv_exact(length) if length else b""
        if opcode == 0x8:
            return None
        return payload.decode("utf-8", errors="replace")

    def recv_command(self, cmd: str, skip_broadcasts: bool = True, timeout: float = 5.0) -> Optional[Dict[str, Any]]:
        """Read frames until one carries ``cmd`` (skipping ``gin`` state pushes)."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            self.socket.settimeout(max(0.1, deadline - time.monotonic()))
            frame = self.recv_frame()
            if frame is None:
                continue
            try:
                payload = json.loads(frame.strip())
            except ValueError:
                continue
            if payload.get("cmd") == cmd:
                return payload
            if not skip_broadcasts:
                return payload
        return None

    def drain(self, seconds: float = 0.3) -> List[Dict[str, Any]]:
        frames = []
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            self.socket.settimeout(max(0.05, deadline - time.monotonic()))
            try:
                frame = self.recv_frame()
            except (socket.timeout, ConnectionError):
                break
            if frame is None:
                break
            try:
                frames.append(json.loads(frame.strip()))
            except ValueError:
                frames.append({"_raw": frame})
        return frames

    def close(self) -> None:
        try:
            self.socket.close()
        except OSError:
            pass
