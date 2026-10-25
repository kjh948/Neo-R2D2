"""Bridge between the ROS node and the MCU UART link (``r2d2_link``).

The reader daemon thread of ``JsonLineTransport`` calls ``_on_line`` on its own
thread, so it only *parses* frames here: status fields are updated in place
(interlock state — cheap attribute writes) and publishable events are appended
to a thread-safe deque. The node drains the deque from a timer on its
executor thread; rclpy publishers must not be touched from the reader thread.
"""
from __future__ import annotations

import collections
import json
import threading
import time
from typing import Any, Deque, Tuple

from .r2d2_link import Commander, JsonLineTransport

# r2d2/mcu_commands.py:18 — the SerialPortCommandReceiver's allow list.
MCU_COMMANDS = ("play_sound", "ready", "btn", "gin")

# r2d2/state.py:14-16 CHARGING_* constants
_CHARGING_NAMES = {0: "NOT_CHARGED", 1: "CHARGING", 2: "CHARGING_FINISHED"}

# r2d2/mcu_commands.py:20 — unmount/finish debounced, mounting immediate.
CHARGING_DEBOUNCE_SECONDS = 3.0


class McuStatus:
    """Last known MCU state (fields mirror the ``gin`` frame, r2d2/models.py
    GinResponse) + the bridge's boot ACK.

    ``Commander`` reads ``.charging`` for its locomotion interlock, so we
    avoid the full file-backed ``RobotState`` (and its side effects on
    state.json). Values are plain attribute writes — safe under the GIL; the
    node re-reads them while draining events.
    """

    def __init__(self) -> None:
        self.charging = 0          # debounced (interlock truth)
        self.battery = -1
        self.arm = False
        self.lightsaber = False
        self.projector = 0
        self.lcd_short_open = False
        self.lcd_long_open = False
        self.mode = 0
        self.head = 0
        self.error = "NO ERROR"
        self.mcu_ready = False


class McuClient:
    """Owns the UART link; exposes the handful of commands the node needs."""

    def __init__(
        self,
        device: str,
        mock: bool = False,
        baudrate: int = 115200,
        read_timeout: float = 1.0,
        logger=None,
        mock_responds: bool = True,
    ) -> None:
        self._log = logger
        self.status = McuStatus()
        self._events: Deque[Tuple[str, Any]] = collections.deque(maxlen=256)
        self._charging_lock = threading.Lock()
        self._charging_timer = None
        self._charging_changed_at = 0.0
        self.transport = JsonLineTransport(
            device=device, baudrate=baudrate, read_timeout=read_timeout,
            mock=mock, mock_responds=mock_responds, on_line=self._on_line,
        )
        self.commander = Commander(self.transport, state=self.status)

    # -- lifecycle ------------------------------------------------------------
    def open(self) -> None:
        self.transport.open()
        # Mirrors MainActivity: announce software readiness to the MCU.
        self.commander.software_ready()

    def close(self) -> None:
        with self._charging_lock:
            if self._charging_timer is not None:
                self._charging_timer.cancel()
                self._charging_timer = None
        self.transport.close()

    @property
    def is_open(self) -> bool:
        return self.transport.is_open

    # -- commands -------------------------------------------------------------
    def move(self, power: int, angle: int) -> bool:
        return self.commander.move(power, angle)

    def stop(self) -> bool:
        return self.commander.move(0, 0)

    def request_gin(self) -> bool:
        return self.commander.gin()

    # -- inbound frames ---------------------------------------------------------
    def drain_events(self):
        """Pop everything queued by the reader thread (executor-thread safe).

        Returns a list of ``(kind, payload)``:
          ("status", None)          — gin frame updated self.status
          ("button", int)           — btn frame value
          ("sound_request", dict)   — play_sound frame {sound_id, interrupt}
        """
        out = []
        while self._events:
            out.append(self._events.popleft())
        return out

    def _on_line(self, text: str) -> None:
        """Parse one inbound JSON line (runs on the UART reader thread)."""
        if not text.startswith("{"):
            return
        try:
            data = json.loads(text)
        except ValueError:
            return
        if not isinstance(data, dict):
            return
        cmd = data.get("cmd")
        if cmd not in MCU_COMMANDS:
            return
        if cmd == "gin":
            self._on_gin(data)
        elif cmd == "ready":
            self.status.mcu_ready = True
            self._events.append(("ready_ack", None))
        elif cmd == "btn":
            try:
                self._events.append(("button", int(data.get("value", 0) or 0)))
            except (TypeError, ValueError):
                pass
        elif cmd == "play_sound":
            try:
                self._events.append(("sound_request", {
                    "sound_id": int(data.get("sound_id", 0) or 0),
                    "interrupt": int(data.get("interrupt", 0) or 0) == 1,
                }))
            except (TypeError, ValueError):
                pass

    def _on_gin(self, data: dict) -> None:
        st = self.status
        try:
            battery = int(data.get("batt", -1) if data.get("batt") is not None else -1)
        except (TypeError, ValueError):
            battery = -1
        st.battery = battery
        st.arm = bool(int(data.get("arm", 0) or 0))
        st.lightsaber = bool(int(data.get("lightsaber", 0) or 0))
        st.projector = int(data.get("projector", 0) or 0)
        # lcd_* are NOT flags: >= 2 means open (r2d2/models.py:78-85)
        st.lcd_short_open = int(data.get("lcd_s", 0) or 0) >= 2
        st.lcd_long_open = int(data.get("lcd_l", 0) or 0) >= 2
        st.mode = int(data.get("mode", 0) or 0)
        st.head = int(data.get("head", 0) or 0)
        error = data.get("error")
        st.error = str(error) if error not in (None, "") else "NO ERROR"

        raw_charging = int(data.get("charging-status", 0) or 0)
        self._apply_charging(raw_charging)
        self._events.append(("status", None))

    def _apply_charging(self, new_state: int) -> None:
        """Port of r2d2/mcu_commands.py:169-206 (charging debounce).

        Going ONTO the dock (== CHARGING) is applied immediately because it
        gates locomotion commands — delaying it would let the robot walk while
        charging. Leaving the dock / finished transitions are applied only
        after they survive ``CHARGING_DEBOUNCE_SECONDS``. A fresh mount frame
        also CANCELS a pending leave timer (mount -> unmount -> remount inside
        the window; the original app left a gap until the next gin poll here —
        tightened, safety-first direction).
        """
        now = time.monotonic()
        with self._charging_lock:
            if new_state == 1:
                self._charging_changed_at = now
                if self._charging_timer is not None:
                    self._charging_timer.cancel()
                    self._charging_timer = None
                self._set_charging(new_state)
                return
            if self.status.charging == new_state:
                return
            self._charging_changed_at = now
            if self._charging_timer is not None:
                self._charging_timer.cancel()
            timer = threading.Timer(
                CHARGING_DEBOUNCE_SECONDS, self._commit_charging, args=(new_state, now))
            timer.daemon = True
            self._charging_timer = timer
            timer.start()

    def _commit_charging(self, new_state: int, changed_at: float) -> None:
        with self._charging_lock:
            if self._charging_changed_at != changed_at:
                return  # superseded by a fresher transition
            self._set_charging(new_state)

    def _set_charging(self, new_state: int) -> None:
        # caller holds _charging_lock
        if self.status.charging == new_state:
            return
        self.status.charging = new_state
        if self._log is not None:
            # Hardware truth 2026-10-25: docked => move/head/mode frames are
            # suppressed by Commander until the debounced state returns to 0.
            name = _CHARGING_NAMES.get(new_state, f"unknown state {new_state}")
            self._log.info(
                f"MCU charging status -> {new_state} ({name}) — locomotion "
                + ("suppressed while docked" if new_state else "allowed again")
            )
