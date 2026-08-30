from __future__ import annotations

import threading
from typing import Callable, Optional

from .leds import LEDLightController
from .log import get_logger

LOG = get_logger("modes")

MODE_READY = 1
MODE_SLEEP = 2
MODE_PAIR = 3
MODE_PATROL = 4
MODE_USER_CONTROL = 5

SLEEP_TIME = 180.0
PATROL_TIME = 60.0
PAIR_TIMEOUT = 30.0
WIFI_CONNECT_TIMEOUT = 30.0
PATROL_LIGHT_STOP_DELAY = 0.1


class SleepController:
    """Port of ``SleepController`` — a single one-shot 180 s arm timer."""

    def __init__(self, on_sleep: Callable[[], None], on_wake: Callable[[], None], sleep_time: float = SLEEP_TIME) -> None:
        self._on_sleep = on_sleep
        self._on_wake = on_wake
        self.sleep_time = sleep_time
        self.is_sleep = False
        self._timer: Optional[threading.Timer] = None
        self._lock = threading.Lock()
        self.restart_sleep_timer()

    def restart_sleep_timer(self) -> None:
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
            self._timer = threading.Timer(self.sleep_time, self._fire_sleep)
            self._timer.daemon = True
            self._timer.start()

    def stop_timer(self) -> None:
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
                self._timer = None

    def _fire_sleep(self) -> None:
        LOG.info("sleep timer triggered")
        self.is_sleep = True
        self._on_sleep()

    def wake(self) -> None:
        before_change = self.is_sleep
        self.is_sleep = False
        self.restart_sleep_timer()
        if before_change:
            self._on_wake()


class PatrolController:
    """Port of ``PatrolController``.

    The walking animation itself lives in the MCU firmware: entering patrol
    writes ``{"cmd":"mode","mode":9}`` once and the MCU then drives the
    obstacle-avoidance behaviour. The host only keeps the patrol light on and
    force-stops after 60 s.
    """

    def __init__(self, on_start: Callable[[], None], on_stop: Callable[[], None], lights: LEDLightController, central=None, patrol_time: float = PATROL_TIME) -> None:
        self._on_start = on_start
        self._on_stop = on_stop
        self.patrol_time = patrol_time
        self.lights = lights
        self.central = central
        self._timer: Optional[threading.Timer] = None
        self._lock = threading.Lock()
        self.is_patrolling = False
        # Wired by ModeController: patrol expiry calls EventHandler.stop_job().
        self.on_stop_job: Optional[Callable[[], None]] = None

    def start_patrol(self) -> None:
        self._on_start()
        self.is_patrolling = True
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
            self._timer = threading.Timer(self.patrol_time, self._patrol_expired)
            self._timer.daemon = True
            self._timer.start()
        self.lights.start_patrol_light()
        if self.central is not None:
            self.central.stop_face_detection(force=True)
            self.central.stop_voice_recognition()

    def _patrol_expired(self) -> None:
        # ``PatrolTimerTask`` only calls ``EventHandler.stopJob()``, which emits
        # mode 0 and tears the patrol mode down.
        LOG.info("patrol timer triggered")
        self.is_patrolling = False
        if self.on_stop_job is not None:
            self.on_stop_job()

    def stop_timers(self) -> None:
        """Cancel the patrol budget without running the stop-side effects."""
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
                self._timer = None
        self.is_patrolling = False

    def stop_patrol(self) -> None:
        self._on_stop()
        self.is_patrolling = False
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
                self._timer = None
        revert = threading.Timer(PATROL_LIGHT_STOP_DELAY, self.lights.stop_patrol_light)
        revert.daemon = True
        revert.start()
        if self.central is not None:
            self.central.start_face_detection()
            self.central.start_voice_recognition()


class PairModeController:
    """Port of ``PairModeController``: QR provisioning with a 30 s re-arm.

    Pair mode swaps the camera between face detection and QR scanning, and the
    whole stage is torn down by a 30 s one-shot that is re-armed after every
    connect attempt, so the robot is never stuck in pair mode.
    """

    def __init__(
        self,
        on_start: Callable[[], None],
        on_stop: Callable[[], None],
        events,
        lights: LEDLightController,
        central,
        wifi=None,
        pair_timeout: float = PAIR_TIMEOUT,
    ) -> None:
        self._on_start = on_start
        self._on_stop = on_stop
        self.pair_timeout = pair_timeout
        self.events = events
        self.lights = lights
        self.central = central
        self.wifi = wifi
        self.pair_key: Optional[str] = None
        self.is_connecting_wifi = False
        self.is_processing = False
        self.started = False
        self._timer: Optional[threading.Timer] = None
        self._lock = threading.RLock()

    def start_pair_mode(self) -> None:
        with self._lock:
            if self.is_processing:
                LOG.debug("drop start command")
                return
            self.started = True
            self.is_processing = True
        self._on_start()
        try:
            if self.central is not None:
                self.central.start_qr_reader()
                self.central.stop_face_detection(force=True)
        finally:
            with self._lock:
                self.is_processing = False
        self.events.restore_light()
        if self.wifi is not None:
            self.wifi.start_scan()
        if self.central is not None:
            self.central.stop_voice_recognition()
        self._arm_stop_timer()

    def stop_pair_mode(self) -> None:
        with self._lock:
            if self.is_processing:
                LOG.debug("drop stop command")
                return
            self.is_processing = True
        LOG.info("start stopping pair mode...")
        self._on_stop()
        self.pair_key = None
        try:
            if self.central is not None:
                self.central.start_face_detection()
                self.central.stop_qr_reader()
        finally:
            with self._lock:
                self.is_processing = False
        self.events.restore_light()
        self._cancel_stop_timer()
        if self.central is not None:
            self.central.start_voice_recognition()

    def _arm_stop_timer(self) -> None:
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
            self._timer = threading.Timer(self.pair_timeout, self._timeout)
            self._timer.daemon = True
            self._timer.start()

    def _cancel_stop_timer(self) -> None:
        with self._lock:
            if self._timer is not None:
                self._timer.cancel()
                self._timer = None

    def _timeout(self) -> None:
        LOG.info("pair timer triggered, pair mode stop")
        self.stop_pair_mode()

    # -- QR provisioning ------------------------------------------------------
    def process_qr_code(self, text: str) -> bool:
        """``ssid/a/<password>/a/<key>`` — the payload ZXing reads off the app."""
        LOG.info("processQRCode: %s", text)
        try:
            parts = text.split("/a/")
            if len(parts) != 3:
                LOG.info("invalid QRCode: wrong data size")
                self.events.fail_in_pair_mode()
                return False
            ssid, password, key = parts
            if not ssid or not password or not key:
                LOG.info("invalid QRCode: empty field")
                self.events.fail_in_pair_mode()
                return False
            LOG.info("ssid:%s key:%s", ssid, key)
            self.is_connecting_wifi = True
            self.events.start_wifi_connection_event()
            if self.central is not None:
                self.central.stop_qr_reader()
            self._cancel_stop_timer()
            self.pair_key = key
            self.connect_wifi(ssid, password)
            return True
        except Exception:
            LOG.exception("qr provisioning failed")
            self.events.fail_in_pair_mode()
            return False

    def connect_wifi(self, ssid: str, password: str) -> None:
        result = self.wifi.connect(ssid, password) if self.wifi else 1
        if result > 0:
            self.connect_wifi_fail()
        elif result == 0:
            self.connect_wifi_success()
        else:
            self.wifi.await_connection_result(
                on_success=self.connect_wifi_success,
                on_unauthorized=self.connect_wifi_fail,
                timeout=WIFI_CONNECT_TIMEOUT,
                on_timeout=lambda: self.connect_wifi_fail() if self.started else None,
            )

    def connect_wifi_success(self) -> None:
        self.is_connecting_wifi = False
        self.events.restore_light()
        self._arm_stop_timer()
        if self.central is not None:
            self.central.start_qr_reader()

    def connect_wifi_fail(self) -> None:
        self.is_connecting_wifi = False
        self.events.fail_in_pair_mode()
        self._arm_stop_timer()
        if self.central is not None:
            self.central.start_qr_reader()

    def success_connection_established(self) -> None:
        self.events.user_grant_access_event()
        self.stop_pair_mode()

    def close(self) -> None:
        self._cancel_stop_timer()


class ModeController:
    """Port of ``ModeController`` — the app-level behaviour state machine.

    ``currentMode`` here (1..5) is *not* the MCU ``mode`` field: the MCU values
    (0..20) are the animation ids the firmware runs, while these five states
    say which host subsystem is in charge. It starts at READY, never at 0.
    """

    def __init__(self, events, lights: LEDLightController, central=None, wifi=None, notify: Optional[Callable[[], None]] = None, controlling_num: Callable[[], int] = lambda: 0, sleep_time: float = SLEEP_TIME, patrol_time: float = PATROL_TIME, pair_timeout: float = PAIR_TIMEOUT) -> None:
        self.events = events
        self.lights = lights
        self._central = None
        self.notify = notify or (lambda: None)
        self._controlling_num = controlling_num
        self.current_mode = MODE_READY
        self._lock = threading.RLock()

        self.sleep = SleepController(self._on_sleep, self._on_wake, sleep_time=sleep_time)
        self.patrol = PatrolController(self._on_patrol_start, self._on_patrol_stop, lights, central, patrol_time=patrol_time)
        self.patrol.on_stop_job = self.events.stop_job
        self.pair = PairModeController(self._on_pair_start, self._on_pair_stop, events, lights, central, wifi, pair_timeout=pair_timeout)
        # Assign through the property so the sub-controllers stay in sync if the
        # app wires CentralController after construction (it breaks a cycle).
        self.central = central

    @property
    def central(self):
        return self._central

    @central.setter
    def central(self, value) -> None:
        self._central = value
        self.patrol.central = value
        self.pair.central = value

    # -- mode -----------------------------------------------------------------
    def get_mode(self) -> int:
        return self.current_mode

    def set_mode(self, mode: int) -> None:
        LOG.info("setMode(%d)", mode)
        with self._lock:
            self.current_mode = mode
        self.notify()

    def reset_mode(self, stop_patrol: bool = False) -> None:
        if self._controlling_num() > 0:
            if stop_patrol and self.current_mode == MODE_PATROL:
                self.stop_sleep_timer()
                self._start_user_control()
            else:
                self.start_user_control_mode()
        else:
            self.set_mode(MODE_READY)

    # -- sleep ----------------------------------------------------------------
    def _on_sleep(self) -> None:
        self.set_mode(MODE_SLEEP)
        if self.central is not None:
            LOG.info("stop by sleep")
            self.central.stop_face_detection(force=True)
            self.central.stop_voice_recognition()

    def _on_wake(self) -> None:
        self.set_mode(MODE_READY)
        if self.central is not None:
            self.central.start_face_detection()
            self.central.start_voice_recognition()
        self.lights.restore_all()

    def wake(self) -> None:
        self.sleep.wake()

    def restart_sleep_timer(self) -> None:
        self.sleep.restart_sleep_timer()

    def stop_sleep_timer(self) -> None:
        self.sleep.stop_timer()

    # -- pair -----------------------------------------------------------------
    def start_pair_mode(self) -> bool:
        if self.pair.is_processing:
            LOG.debug("drop start pair")
            return False
        self.pair.start_pair_mode()
        return True

    def stop_pair_mode(self) -> bool:
        if self.pair.is_processing:
            LOG.debug("drop stop pair")
            return False
        self.pair.stop_pair_mode()
        return True

    def _on_pair_start(self) -> None:
        if self.current_mode == MODE_PATROL:
            self.stop_patrol_mode()
        self.set_mode(MODE_PAIR)
        self.stop_sleep_timer()

    def _on_pair_stop(self) -> None:
        self.reset_mode(stop_patrol=False)
        self.restart_sleep_timer()

    def get_pair_key(self) -> Optional[str]:
        return self.pair.pair_key

    def is_connecting_wifi_in_pair_mode(self) -> bool:
        return self.pair.is_connecting_wifi

    def success_connection_in_pair_mode(self) -> None:
        self.pair.success_connection_established()

    def process_qr_code_in_pair_mode(self, text: str) -> None:
        self.pair.process_qr_code(text)

    # -- patrol ---------------------------------------------------------------
    def start_patrol_mode(self) -> None:
        LOG.info("start Patrol")
        if self.current_mode == MODE_PAIR:
            self.stop_pair_mode()
        self.stop_sleep_timer()
        self.patrol.start_patrol()

    def stop_patrol_mode(self) -> None:
        LOG.info("stop Patrol")
        self.restart_sleep_timer()
        self.patrol.stop_patrol()

    def _on_patrol_start(self) -> None:
        self.set_mode(MODE_PATROL)

    def _on_patrol_stop(self) -> None:
        self.reset_mode(stop_patrol=True)

    # -- user control ---------------------------------------------------------
    def start_user_control_mode(self) -> None:
        if self.current_mode in (MODE_PAIR, MODE_PATROL):
            LOG.info("user control cannot start in pair/patrol")
            return
        self.stop_sleep_timer()
        self._start_user_control()

    def stop_user_control_mode(self) -> None:
        if self.current_mode != MODE_USER_CONTROL:
            LOG.debug("stop user control mode in other mode...")
            return
        self.restart_sleep_timer()
        self._stop_user_control()

    def _start_user_control(self) -> None:
        self.set_mode(MODE_USER_CONTROL)
        if self.central is not None:
            self.central.stop_face_detection(force=True)

    def _stop_user_control(self) -> None:
        if self.central is not None:
            self.central.start_face_detection()
        self.set_mode(MODE_READY)

    def close(self) -> None:
        # Teardown must not run the normal stop paths: they restart face
        # detection and voice recognition, which would re-arm exactly the
        # threads the app is trying to shut down.
        self.sleep.stop_timer()
        self.patrol.stop_timers()
        self.pair.close()
