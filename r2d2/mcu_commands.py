from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

from .leds import LEDLightController
from .log import get_logger
from .models import Command, GinResponse
from .state import CHARGING_CHARGING, LOW_BATTERY_PERCENTAGE, NO_ERROR

LOG = get_logger("mcu")

# ``SerialPortCommandReceiver`` only accepts these four ``cmd`` values from the
# MCU; everything else on the serial line is ignored.
MCU_COMMANDS = ("play_sound", "ready", "btn", "gin")

CHARGING_DEBOUNCE_SECONDS = 3.0


@dataclass
class McuHooks:
    """Side-effect seams the receiver needs from the network/wifi layers."""

    ap_mode_toggle: Optional[Callable[[], Any]] = None
    start_pair_mode: Optional[Callable[[], Any]] = None
    stop_pair_mode: Optional[Callable[[], Any]] = None
    notify: Optional[Callable[[], Any]] = field(default=lambda: None)


class McuCommandReceiver:
    """Port of ``SerialPortCommandReceiver`` — turns MCU frames into actions.

    A frame is accepted only if it parses as a JSON object whose first byte is
    ``{`` (the framing itself already happened in the transport). The MCU never
    pushes unsolicited ``play_sound`` for host-owned clips without this layer
    routing it to the local sound mixer.
    """

    def __init__(
        self,
        event_handler,
        state,
        lights: LEDLightController,
        mode_controller=None,
        hooks: Optional[McuHooks] = None,
    ) -> None:
        self.events = event_handler
        self.state = state
        self.lights = lights
        self.mode_controller = mode_controller
        self.hooks = hooks or McuHooks()
        self._charging_timer: Optional[threading.Timer] = None
        self._charging_changed_at = 0.0
        self._lock = threading.RLock()

    # -- entry point ----------------------------------------------------------
    def interpret_command(self, text: str) -> Optional[str]:
        text = text.strip()
        if not text.startswith("{") or len(text) <= 2:
            return None
        try:
            data = json.loads(text)
        except ValueError:
            LOG.debug("mcu line is not valid JSON: %r", text[:120])
            return None
        if not isinstance(data, dict):
            return None

        command = Command(data)
        cmd = command.cmd
        try:
            if cmd == "play_sound":
                self.events.play_sound(command.sound_id, command.interrupt == 1)
            elif cmd == "ready":
                self.events.cancel_ready()
            elif cmd == "btn":
                self._handle_button(command.value)
            elif cmd == "gin":
                self.update_robot(GinResponse(data))
            else:
                LOG.debug("ignoring unknown mcu cmd %r", cmd)
                return None
        except Exception:  # pragma: no cover - mirrors the app's catch-all
            LOG.exception("handling MCU frame %r failed", text[:120])
        return cmd

    # -- the physical button panel -------------------------------------------
    def _handle_button(self, value: int) -> None:
        if value == 1:
            self.events.power_off()
        elif value == 2:
            self._call(self.hooks.ap_mode_toggle, "ap_mode_toggle")
        elif value == 3:
            if self._mode() == 3:
                self._call(self.hooks.stop_pair_mode, "stop_pair_mode")
            else:
                self._call(self.hooks.start_pair_mode, "start_pair_mode")
        elif value == 4:
            self.events.lightsaber()
        elif value == 5:
            self.events.arm()
        elif value == 6:
            self.events.patrol()
        else:
            LOG.debug("unmapped btn value %d", value)

    def _mode(self) -> int:
        return self.mode_controller.get_mode() if self.mode_controller is not None else 0

    @staticmethod
    def _call(fn: Optional[Callable[[], Any]], what: str) -> None:
        if fn is None:
            LOG.debug("%s not wired", what)
            return
        fn()

    # -- status report --------------------------------------------------------
    def update_robot(self, gin: GinResponse) -> None:
        updated = False

        arm = gin.arm_on
        lightsaber = gin.lightsaber_on

        old_battery = self.state.battery
        if old_battery != gin.batt:
            self.state.battery = gin.batt
            updated = True
            # Crossing the low-battery line in either direction re-resolves the
            # whole light state, because FRONT_BASE_BATTERY_LOW sits in the
            # front-mode priority ladder.
            if (old_battery < LOW_BATTERY_PERCENTAGE <= gin.batt) or (
                old_battery >= LOW_BATTERY_PERCENTAGE > gin.batt
            ):
                self.lights.restore_all()

        if self.state.lightsaber != lightsaber:
            self.state.lightsaber = lightsaber
            updated = True

        self._apply_charging(gin.charging_status)

        if self.state.projector != gin.projector:
            self.state.projector = gin.projector
            updated = True

        if self.state.arm != arm:
            self.state.arm = arm
            updated = True

        if self.state.long_lcd != gin.long_lcd_open:
            self.state.long_lcd = gin.long_lcd_open
            updated = True

        if self.state.short_lcd != gin.short_lcd_open:
            self.state.short_lcd = gin.short_lcd_open
            updated = True

        error = gin.error_text or NO_ERROR
        if self.state.error != error:
            self.state.error = error
            updated = True

        if updated:
            self._notify()

    def _apply_charging(self, new_state: int) -> None:
        current = self.state.charging
        if current == new_state:
            return
        now = time.monotonic()
        with self._lock:
            self._charging_changed_at = now
            if new_state == CHARGING_CHARGING:
                # Going *onto* the dock is applied immediately; it gates the
                # locomotion commands in Commander, so delaying it would let the
                # robot walk while it is charging.
                self.state.charging = new_state
                self.lights.charging_light_start()
                self._notify()
                return
            if self._charging_timer is not None:
                self._charging_timer.cancel()
            timer = threading.Timer(
                CHARGING_DEBOUNCE_SECONDS,
                self._commit_charging,
                args=(new_state, now),
            )
            timer.daemon = True
            self._charging_timer = timer
            timer.start()

    def _commit_charging(self, new_state: int, changed_at: float) -> None:
        with self._lock:
            if self._charging_changed_at != changed_at:
                LOG.debug("charging debounce superseded, skipping %d", new_state)
                return
        if self.state.charging != new_state:
            self.state.charging = new_state
            self._notify()
        if new_state == 2:
            self.lights.charged_light_start()
        elif new_state == 0:
            self.lights.charging_light_stop()

    def _notify(self) -> None:
        if self.hooks.notify is not None:
            self.hooks.notify()

    def close(self) -> None:
        with self._lock:
            if self._charging_timer is not None:
                self._charging_timer.cancel()
                self._charging_timer = None
