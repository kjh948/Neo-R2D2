from __future__ import annotations

import threading
from typing import Callable, Optional, Tuple

from .commander import UNCHANGE, Commander
from .log import get_logger

LOG = get_logger("leds")

# ``LEDLightController`` channel effect modes (``LEDJob.MODE_*``). The animated
# patterns live in the MCU firmware; the host only picks an effect index per
# channel.
MODE_NONE = 0
MODE_OFF = 1
MODE_ON = 2
MODE_SF1 = 3
MODE_SF2 = 4
MODE_FF1 = 5
MODE_FF2 = 6

# Back strip (g + y channels).
BACK_BASE_MODE_LAN_WITH_WIFI = 20
BACK_BASE_MODE_LAN_WITHOUT_WIFI = 21
BACK_BASE_POWER_OFF = 22
BACK_BASE_AP_MODE = 23
BACK_BASE_MODE_AP_CONNECTING = 24
BACK_BASE_SLEEP_IN_AP = 25
BACK_BASE_SLEEP_IN_LOCAL_NETWORK = 26
BACK_SPECIAL_MODE_SLEEP = 201
BACK_SPECIAL_MODE_VOICE_RECOGNITION = 202
BACK_UNCHANGE = -1

# Front dome (r + b channels).
FRONT_BASE_MODE_READY = 0
FRONT_BASE_MODE_PAIR = 1
FRONT_BASE_POWER_OFF = 2
FRONT_BASE_BATTERY_LOW = 3
FRONT_BASE_SLEEP = 4
FRONT_SPECIAL_CHARGING = 5
FRONT_SPECIAL_CHARGED = 6
FRONT_SPECIAL_MODE_PATROL = 102
FRONT_SPECIAL_PAIR_FAIL = 103
FRONT_SPECIAL_CONNECT_WIFI = 104
FRONT_SPECIAL_FACE_DETECTION = 105
FRONT_UNCHANGE = -1

# App-level modes referenced by the base-mode priority ladders.
APP_MODE_SLEEP = 2
APP_MODE_PAIR = 3
APP_MODE_PATROL = 4

FRONT_PATTERNS: dict[int, Tuple[int, int]] = {
    FRONT_BASE_MODE_READY: (MODE_ON, MODE_ON),
    FRONT_BASE_MODE_PAIR: (MODE_OFF, MODE_SF1),
    FRONT_BASE_POWER_OFF: (MODE_FF1, MODE_OFF),
    FRONT_BASE_BATTERY_LOW: (MODE_ON, MODE_OFF),
    FRONT_BASE_SLEEP: (MODE_OFF, MODE_OFF),
    FRONT_SPECIAL_CHARGING: (MODE_SF1, MODE_SF1),
    FRONT_SPECIAL_CHARGED: (MODE_OFF, MODE_ON),
    FRONT_SPECIAL_MODE_PATROL: (MODE_SF1, MODE_SF2),
    FRONT_SPECIAL_PAIR_FAIL: (MODE_SF1, MODE_OFF),
    FRONT_SPECIAL_CONNECT_WIFI: (MODE_OFF, MODE_FF1),
    FRONT_SPECIAL_FACE_DETECTION: (MODE_OFF, MODE_ON),
}

BACK_PATTERNS: dict[int, Tuple[int, int]] = {
    BACK_BASE_MODE_LAN_WITH_WIFI: (MODE_ON, MODE_OFF),
    BACK_BASE_MODE_LAN_WITHOUT_WIFI: (MODE_FF1, MODE_OFF),
    BACK_BASE_POWER_OFF: (MODE_OFF, MODE_OFF),
    BACK_BASE_AP_MODE: (MODE_OFF, MODE_ON),
    BACK_BASE_MODE_AP_CONNECTING: (MODE_OFF, MODE_FF1),
    BACK_BASE_SLEEP_IN_AP: (MODE_OFF, MODE_SF1),
    BACK_BASE_SLEEP_IN_LOCAL_NETWORK: (MODE_SF1, MODE_OFF),
    BACK_SPECIAL_MODE_SLEEP: (MODE_OFF, MODE_OFF),
    BACK_SPECIAL_MODE_VOICE_RECOGNITION: (MODE_SF1, MODE_SF2),
}


class LightContext:
    """Read-only view of the subsystems that decide the resting light state.

    ``LEDLightController.getFrontBaseMode``/``getBackBaseMode`` consult five
    other singletons; the port injects them as callables so the light layer can
    be unit tested without a camera, a wifi stack or a mode machine.
    """

    def __init__(
        self,
        mode: Callable[[], int] = lambda: 1,
        is_ap_mode: Callable[[], bool] = lambda: False,
        is_ap_connecting: Callable[[], bool] = lambda: False,
        network_connected: Callable[[], bool] = lambda: False,
        voice_recognition_active: Callable[[], bool] = lambda: False,
        face_detected: Callable[[], bool] = lambda: False,
    ) -> None:
        self.mode = mode
        self.is_ap_mode = is_ap_mode
        self.is_ap_connecting = is_ap_connecting
        self.network_connected = network_connected
        self.voice_recognition_active = voice_recognition_active
        self.face_detected = face_detected


class LEDLightController:
    """Port of ``LEDLightController``.

    Only sends the LED channels that actually changed, and latches hard off
    once the power-off pattern has been shown, exactly like the original
    (``backMode == 22 || frontMode == 2`` blocks every later change).
    """

    def __init__(self, commander: Commander, state, ctx: Optional[LightContext] = None) -> None:
        self.commander = commander
        self.state = state
        self.ctx = ctx or LightContext()
        self.front_mode = -1
        self.back_mode = -1
        self._lock = threading.RLock()
        self._pair_error_timer: Optional[threading.Timer] = None

    # -- tables ---------------------------------------------------------------
    @staticmethod
    def get_led_job_from_mode(front_mode: int, back_mode: int) -> Tuple[int, int, int, int]:
        """Return ``(r, b, y, g)`` — the LEDJob constructor order."""
        r, b = FRONT_PATTERNS.get(front_mode, (MODE_NONE, MODE_NONE))
        g, y = BACK_PATTERNS.get(back_mode, (MODE_NONE, MODE_NONE))
        return r, b, y, g

    def get_back_base_mode(self) -> int:
        if self.ctx.mode() == APP_MODE_SLEEP:
            return BACK_BASE_SLEEP_IN_AP if self.ctx.is_ap_mode() else BACK_BASE_SLEEP_IN_LOCAL_NETWORK
        if self.ctx.voice_recognition_active():
            return BACK_SPECIAL_MODE_VOICE_RECOGNITION
        if self.ctx.is_ap_mode():
            return BACK_BASE_MODE_AP_CONNECTING if self.ctx.is_ap_connecting() else BACK_BASE_AP_MODE
        if self.ctx.network_connected():
            return BACK_BASE_MODE_LAN_WITH_WIFI
        return BACK_BASE_MODE_LAN_WITHOUT_WIFI

    def get_front_base_mode(self) -> int:
        mode = self.ctx.mode()
        if mode == APP_MODE_PATROL:
            return FRONT_SPECIAL_MODE_PATROL
        if self.ctx.face_detected():
            return FRONT_SPECIAL_FACE_DETECTION
        if mode == APP_MODE_PAIR:
            return FRONT_SPECIAL_CONNECT_WIFI if self.ctx.is_ap_connecting() else FRONT_BASE_MODE_PAIR
        charging = self.state.charging
        if charging == 1:
            return FRONT_SPECIAL_CHARGING
        if charging == 2:
            return FRONT_SPECIAL_CHARGED
        if self.state.low_battery:
            return FRONT_BASE_BATTERY_LOW
        return FRONT_BASE_SLEEP if mode == APP_MODE_SLEEP else FRONT_BASE_MODE_READY

    # -- the one writer -------------------------------------------------------
    def change_light(self, front: int, back: int) -> bool:
        with self._lock:
            if self.back_mode == BACK_BASE_POWER_OFF or self.front_mode == FRONT_BASE_POWER_OFF:
                LOG.debug("power-off light latched: ignoring further LED changes")
                return False
            if self.front_mode == front:
                front = FRONT_UNCHANGE
            if self.back_mode == back:
                back = BACK_UNCHANGE
            if front != FRONT_UNCHANGE:
                self.front_mode = front
            if back != BACK_UNCHANGE:
                self.back_mode = back
            if front == FRONT_UNCHANGE and back == BACK_UNCHANGE:
                return True
            r, b, y, g = self.get_led_job_from_mode(front, back)
            return self.commander.led(r=r, b=b, y=y, g=g)

    # -- named transitions ----------------------------------------------------
    def change_to_ready_light(self) -> bool:
        return self.change_light(self.front_mode, self.back_mode)

    def restore_front_base_mode(self) -> bool:
        return self.change_light(self.get_front_base_mode(), BACK_UNCHANGE)

    def restore_back_base_mode(self) -> bool:
        return self.change_light(FRONT_UNCHANGE, self.get_back_base_mode())

    def restore_all(self) -> bool:
        return self.change_light(self.get_front_base_mode(), self.get_back_base_mode())

    def start_patrol_light(self) -> bool:
        return self.change_light(FRONT_SPECIAL_MODE_PATROL, self.get_back_base_mode())

    def stop_patrol_light(self) -> bool:
        if self.front_mode != FRONT_SPECIAL_MODE_PATROL:
            return False
        return self.restore_front_base_mode()

    def face_detect_light_start(self) -> bool:
        return self.change_light(FRONT_SPECIAL_FACE_DETECTION, BACK_UNCHANGE)

    def face_detect_light_stop(self) -> bool:
        if self.front_mode != FRONT_SPECIAL_FACE_DETECTION:
            return False
        return self.restore_front_base_mode()

    def charging_light_start(self) -> bool:
        return self.change_light(FRONT_SPECIAL_CHARGING, BACK_UNCHANGE)

    def charged_light_start(self) -> bool:
        return self.change_light(FRONT_SPECIAL_CHARGED, BACK_UNCHANGE)

    def charging_light_stop(self) -> bool:
        return self.restore_front_base_mode()

    def power_off_light(self) -> bool:
        return self.change_light(FRONT_BASE_POWER_OFF, BACK_BASE_POWER_OFF)

    def connect_wifi_mode(self) -> bool:
        if self.front_mode == FRONT_SPECIAL_CONNECT_WIFI:
            return False
        return self.change_light(FRONT_SPECIAL_CONNECT_WIFI, BACK_UNCHANGE)

    def fail_in_pair_mode(self) -> bool:
        changed = False
        if self.front_mode != FRONT_SPECIAL_PAIR_FAIL:
            changed = self.change_light(FRONT_SPECIAL_PAIR_FAIL, BACK_UNCHANGE)
        if self._pair_error_timer is not None:
            self._pair_error_timer.cancel()
        timer = threading.Timer(2.0, self.restore_front_base_mode)
        timer.daemon = True
        self._pair_error_timer = timer
        timer.start()
        return changed

    def start_sleep_light(self) -> bool:
        # ``LEDLightController.startSleepLight()`` is an empty method body in
        # the app; sleep state is expressed through the base-mode ladders.
        return False

    def close(self) -> None:
        if self._pair_error_timer is not None:
            self._pair_error_timer.cancel()
            self._pair_error_timer = None
