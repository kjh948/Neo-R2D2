from __future__ import annotations

import threading
from typing import Any, Dict, Optional

from .log import get_logger
from .models import Command
from .state import RobotState
from .transport import JsonLineTransport

LOG = get_logger("commander")

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

# ``ModeJob.prohabittedModeWhileCharging`` — typo in the original, kept here as
# the canonical name for cross-referencing.
MODES_PROHIBITED_WHILE_CHARGING = frozenset({1, 2, 3, 4, 5, 9, 10, 12, 15})

# Sentinel accepted by ``Commander.LED``/``Commander.LCD``: the key is omitted
# from the frame, which the MCU firmware reads as "leave this channel alone".
UNCHANGE = -1


class Commander:
    """Serialises host -> MCU commands onto the UART link.

    Mirrors ``Commander.java`` including its charging interlocks: while the MCU
    reports a charging state the locomotion commands are dropped silently, and
    the animated modes are refused. ``send()`` returns ``False`` on a write
    failure rather than the original's always-``True`` result, so callers can
    actually tell that the frame did not leave the box.
    """

    def __init__(self, transport: JsonLineTransport, state: Optional[RobotState] = None) -> None:
        self.transport = transport
        self.state = state
        self._lock = threading.Lock()

    # -- plumbing -------------------------------------------------------------
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
        # ``Commander.move`` and friends are wrapped in
        # ``if (getRobotCharging() == 0)``, so charging silences them.
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
        # Insertion order matches the Java (r, b, y, g) so byte-level diffs of
        # the frames against a capture of the original app still line up.
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
        """Ask the MCU to play a clip.

        The Android app never sends this (it plays sounds locally), but the
        protocol reserves the frame and the standalone scripts use it.
        """
        return self._send({"cmd": CMD_PLAY_SOUND, "sound_id": int(sound_id), "interrupt": int(interrupt)})

    @staticmethod
    def payload_from_command(command: Command) -> Optional[Dict[str, Any]]:
        """Re-serialise a parsed inbound command for pass-through forwarding."""
        if not command.cmd:
            return None
        payload: Dict[str, Any] = {"cmd": command.cmd}
        for key in ("power", "angle", "dir", "mode", "value", "sound_id", "interrupt", "url"):
            value = getattr(command, key, 0)
            if value not in (0, None, ""):
                payload[key] = value
        for key in ("r", "b", "y", "g", "s", "l"):
            value = getattr(command, key, UNCHANGE)
            if value != UNCHANGE:
                payload[key] = value
        return payload
