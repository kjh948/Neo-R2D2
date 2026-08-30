from __future__ import annotations

import abc
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, List, Optional

from .commander import Commander
from .leds import LEDLightController
from .log import get_logger
from .sound import (
    ALARMED_THRILL,
    DANGER_DANGER,
    HAPPINESS_CONFIRMATION,
    HAPPY_THREE_CHIRP,
    LONELY_HELLO,
    SHORT_RASPBERRY,
    SING_SONG_RESPONSE,
    SOUND_ANGLE,
    SOUND_STARK,
    STARWAR01,
    STARWAR03,
    SoundPlayer,
)

LOG = get_logger("events")

# ``ModeJob`` constants.
MODE_STOP = 0
MODE_WAKE = 1
MODE_TURN_AROUND = 2
MODE_TURN_LEFT = 3
MODE_TURN_RIGHT = 4
MODE_GO_FORWARD = 5
MODE_LIGHTSABER = 6
MODE_WHO_ARE_YOU = 7
MODE_NOT_RECOGNIZE = 8
MODE_PATROL = 9
MODE_DANCE = 10
MODE_WALK_CIRCLE = 12
MODE_FLASH_FRONT_LCD = 13
MODE_FLASH_BACK_LCD = 14
MODE_SHAKE_HEAD = 15
MODE_HAND_JOB = 16
MODE_SHORT_LCD = 17
MODE_LONG_LCD = 18
MODE_PROJECTOR_1 = 19
MODE_PROJECTOR_2 = 20

LCD_CLOSED = 1
LCD_OPEN = 2

APP_MODE_PATROL = 4

# Head angles used by the greeting / negative head animations.
SHAKE_ANGLE = 45
NOD_ANGLE = 40

WALK_LOOP_PERIOD = 2.0
WALK_LOOP_STOP_DELAY = 1.0
PAIR_ERROR_REVERT = 2.0


# --------------------------------------------------------------------------
# Jobs — pure value carriers, exactly like ``Model/EventJob/*``.
# --------------------------------------------------------------------------
class EventJob(abc.ABC):
    command: str = ""

    def __init__(self, delay: int = 0) -> None:
        self.delay = int(delay)


@dataclass
class MoveJob(EventJob):
    power: int = 0
    angle: int = 0
    command: str = field(default="move", init=False)

    def __init__(self, power: int, angle: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.power = int(power)
        self.angle = int(angle)


@dataclass
class MoveHeadJob(EventJob):
    angle: int = 0
    command: str = field(default="head-angle", init=False)

    def __init__(self, angle: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.angle = int(angle)


@dataclass
class ShiftHeadJob(EventJob):
    angle: int = 0
    command: str = field(default="head-shift", init=False)

    def __init__(self, angle: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.angle = int(angle)


@dataclass
class MoveHeadDirJob(EventJob):
    dir: int = 0
    command: str = field(default="head-dir", init=False)

    def __init__(self, direction: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.dir = int(direction)


@dataclass
class ModeJob(EventJob):
    mode: int = 0
    command: str = field(default="mode", init=False)

    def __init__(self, mode: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.mode = int(mode)


@dataclass
class LEDJob(EventJob):
    command: str = field(default="led", init=False)

    def __init__(self, r: int, b: int, y: int, g: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.r, self.b, self.y, self.g = int(r), int(b), int(y), int(g)


@dataclass
class LCDJob(EventJob):
    command: str = field(default="lcd", init=False)

    def __init__(self, s: int, l: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.s, self.l = int(s), int(l)


@dataclass
class SoundJob(EventJob):
    command: str = field(default="play_sound", init=False)

    def __init__(self, sound_id: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.sound_id = int(sound_id)


@dataclass
class HandJob(EventJob):
    power: int = 0
    command: str = field(default="arm", init=False)

    def __init__(self, power: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.power = int(power)


@dataclass
class LightsaberJob(EventJob):
    power: int = 0
    command: str = field(default="lightsaber", init=False)

    def __init__(self, power: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.power = int(power)


@dataclass
class ProjectorJob(EventJob):
    mode: int = 0
    command: str = field(default="projector", init=False)

    def __init__(self, mode: int, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.mode = int(mode)


@dataclass
class PatrolJob(EventJob):
    enable: bool = False
    command: str = field(default="patrol", init=False)

    def __init__(self, enable: bool, delay: int = 0) -> None:
        EventJob.__init__(self, delay)
        self.enable = bool(enable)


# --------------------------------------------------------------------------
class EventHandler:
    """Port of ``EventHandler`` — the behaviour layer between clients and MCU.

    Every user-visible action builds an ordered job list, and one worker thread
    drains it. A job's ``delay`` is applied *before* that job runs, which is
    what makes the head-nod and dance animations land in sequence. Replacing
    the queue (any new action) cancels the pending job and first writes
    ``{"cmd":"mode","mode":0}`` to stop whatever the MCU was animating.
    """

    def __init__(
        self,
        commander: Commander,
        sound_player: SoundPlayer,
        led_controller: LEDLightController,
        state,
        mode_controller=None,
        notify: Optional[Callable[[], None]] = None,
        shutdown_hook: Optional[Callable[[], None]] = None,
    ) -> None:
        self.commander = commander
        self.sound_player = sound_player
        self.led_controller = led_controller
        self.state = state
        self.mode_controller = mode_controller
        self.notify = notify or (lambda: None)
        self.shutdown_hook = shutdown_hook

        self._queue: List[EventJob] = []
        self._lock = threading.RLock()
        self._wakeup = threading.Event()
        self._closed = threading.Event()
        self._generation = 0
        self._worker: Optional[threading.Thread] = None
        self.is_busy = False

        self._move_timer: Optional[threading.Timer] = None
        self._move_stop_timer: Optional[threading.Timer] = None
        self._move_phase = 0
        self._move_schedule_start = False

    # -- queue plumbing -------------------------------------------------------
    def _wake_mode_controller(self) -> None:
        if self.mode_controller is not None:
            try:
                self.mode_controller.wake()
            except Exception:  # pragma: no cover
                LOG.exception("wake() failed")

    def _current_mode(self) -> int:
        if self.mode_controller is None:
            return 0
        return self.mode_controller.get_mode()

    def start(self) -> None:
        if self._worker is not None and self._worker.is_alive():
            return
        self._worker = threading.Thread(target=self._run, name="events", daemon=True)
        self._worker.start()

    def close(self) -> None:
        self._closed.set()
        self._generation += 1
        self._cancel_move_timers()
        self._wakeup.set()

    def _run(self) -> None:
        while not self._closed.is_set():
            with self._lock:
                job = self._queue.pop(0) if self._queue else None
                token = self._generation
                if job is None:
                    self.is_busy = False
            if job is None:
                self._wakeup.wait(0.5)
                self._wakeup.clear()
                continue
            self.is_busy = True
            if not self._wait_for_job(job, token):
                continue
            self.execute_job(job)

    def _wait_for_job(self, job: EventJob, token: int) -> bool:
        """Sleep out the job's lead-in delay; abort if the queue is replaced."""
        if not job.delay:
            return True
        deadline = time.monotonic() + job.delay / 1000.0
        while time.monotonic() < deadline:
            if self._closed.is_set():
                return False
            with self._lock:
                if self._generation != token:
                    LOG.debug("queue replaced during delay, dropping %s", type(job).__name__)
                    return False
            time.sleep(min(0.05, max(0.0, deadline - time.monotonic())))
        with self._lock:
            return self._generation == token and not self._closed.is_set()

    def execute_job(self, job: EventJob) -> None:
        self._wake_mode_controller()
        try:
            self._dispatch(job)
        except Exception:  # pragma: no cover - mirrors the app's catch-all
            LOG.exception("job %s failed", type(job).__name__)

    def _dispatch(self, job: EventJob) -> None:
        if isinstance(job, MoveJob):
            self.commander.move(job.power, job.angle)
        elif isinstance(job, MoveHeadJob):
            self.commander.move_head_angle(job.angle)
        elif isinstance(job, ShiftHeadJob):
            self.commander.head_shift(job.angle)
        elif isinstance(job, MoveHeadDirJob):
            self.commander.move_head_direction(job.dir)
        elif isinstance(job, ModeJob):
            self.commander.mode(job.mode)
        elif isinstance(job, HandJob):
            if self.commander.extend_arm(job.power):
                self.state.arm = job.power != 0
                self.notify()
        elif isinstance(job, LightsaberJob):
            if self.commander.lightsaber(job.power):
                self.state.lightsaber = job.power != 0
                self.notify()
        elif isinstance(job, ProjectorJob):
            self._execute_projector(job)
        elif isinstance(job, LCDJob):
            if self.commander.lcd(job.s, job.l):
                self.state.long_lcd = job.l != LCD_CLOSED
                self.state.short_lcd = job.s != LCD_CLOSED
                self.notify()
        elif isinstance(job, LEDJob):
            self.commander.led(job.r, job.b, job.y, job.g)
        elif isinstance(job, SoundJob):
            self.play_sound(job.sound_id, True)
        elif isinstance(job, PatrolJob):
            ok = self.commander.mode(MODE_PATROL if job.enable else MODE_STOP)
            if ok and self.mode_controller is not None:
                if job.enable:
                    self.mode_controller.start_patrol_mode()
                else:
                    self.mode_controller.stop_patrol_mode()
        else:  # pragma: no cover - defensive
            LOG.warning("unknown job type %s", type(job).__name__)

    def _execute_projector(self, job: ProjectorJob) -> None:
        if job.mode == 0:
            if self.commander.projector_mode(0):
                self.state.projector = 0
                self.notify()
            self.sound_player.pause()
            return
        if not self.commander.projector_mode(job.mode):
            return
        self.state.projector = job.mode
        sound_id = STARWAR01 if job.mode == 1 else STARWAR03
        if job.mode in (1, 2):
            self.play_sound(
                sound_id,
                True,
                on_finished=self._projector_finished,
            )
        self.notify()

    def _projector_finished(self) -> None:
        if self.commander.projector_mode(0):
            self.state.projector = 0
            self.notify()

    # -- queue control --------------------------------------------------------
    def stop_job(self) -> None:
        self._generation += 1
        with self._lock:
            self._queue.clear()
            self.is_busy = False
        self._wakeup.set()
        self.commander.mode(MODE_STOP)
        if self._current_mode() == APP_MODE_PATROL and self.mode_controller is not None:
            self.mode_controller.stop_patrol_mode()

    def _begin(self, jobs: List[EventJob]) -> None:
        with self._lock:
            self.stop_job()
            self._queue = list(jobs)
            self.is_busy = bool(self._queue)
            self._generation += 1
        self._wakeup.set()
        self.start()

    @property
    def pending_jobs(self) -> List[EventJob]:
        with self._lock:
            return list(self._queue)

    # -- driving sound loop ---------------------------------------------------
    def _cancel_move_timers(self) -> None:
        for timer in (self._move_timer, self._move_stop_timer):
            if timer is not None:
                timer.cancel()
        self._move_timer = None
        self._move_stop_timer = None
        self._move_schedule_start = False

    def _start_move_schedule(self) -> None:
        self._move_schedule_start = True
        self._schedule_walk_chirp(WALK_LOOP_PERIOD)

    def _schedule_walk_chirp(self, delay: float) -> None:
        if self._move_schedule_start:
            self._move_timer = threading.Timer(delay, self._walk_tick)
            self._move_timer.daemon = True
            self._move_timer.start()

    def _walk_tick(self) -> None:
        if not self._move_schedule_start:
            return
        if self._move_phase == 0:
            self.play_sound(SING_SONG_RESPONSE, True)
        elif self._move_phase == 2:
            from .sound import CURT_REPLY

            self.play_sound(CURT_REPLY, True)
        self._move_phase = (self._move_phase + 1) % 4
        self._schedule_walk_chirp(WALK_LOOP_PERIOD)

    def cancel_move_schedule(self) -> None:
        self._move_schedule_start = False
        if self._move_timer is not None:
            self._move_timer.cancel()
            self._move_timer = None

    # -- direct MCU setters (no queue) ---------------------------------------
    def software_ready(self) -> None:
        self.led_controller.change_to_ready_light()
        self.commander.software_ready()

    def cancel_ready(self) -> None:
        # The app's ``readyTimer`` is never assigned, so the MCU's ``ready``
        # ack only means "the MCU is alive" — there is no timeout to cancel.
        LOG.debug("mcu ready acknowledged")

    def reset(self) -> None:
        self.commander.reset()

    def reset_mcu(self) -> None:
        self.commander.send_raw("''")

    def make_some_noise(self) -> None:
        self.play_sound(DANGER_DANGER, True)

    def play_sound(self, sound_id: int, interrupt: bool = True, on_finished=None) -> bool:
        return self.sound_player.play_id(sound_id, interrupt, on_finished)

    def restore_light(self) -> None:
        self.led_controller.restore_all()

    def change_head_dir_power(self, power: int) -> None:
        self.commander.change_head_power(power)

    def change_leg_power(self, power: int) -> None:
        self.commander.change_leg_power(power)

    def goto_sleep(self) -> None:
        self.led_controller.start_sleep_light()

    def power_off(self) -> None:
        self.led_controller.power_off_light()
        if self.commander.power_off() and self.shutdown_hook is not None:
            self.shutdown_hook()

    # -- queued behaviours ----------------------------------------------------
    def move(self, power: int, angle: int) -> None:
        self._begin([MoveJob(power, angle, 0)])
        if angle in (0, 180):
            if not self._move_schedule_start:
                self._start_move_schedule()
            if self._move_stop_timer is not None:
                self._move_stop_timer.cancel()
            self._move_stop_timer = threading.Timer(WALK_LOOP_STOP_DELAY, self.cancel_move_schedule)
            self._move_stop_timer.daemon = True
            self._move_stop_timer.start()

    def move_head(self, angle: int) -> None:
        self._begin([MoveHeadJob(angle, 0)])

    def shift_head(self, angle: int) -> None:
        # ``shiftHead`` enqueues a MoveHeadJob in the app too: ``head-shift`` is
        # only reachable from FaceDetection, not from this entry point.
        self._begin([MoveHeadJob(angle, 0)])

    def move_head_dir(self, direction: int) -> None:
        self._begin([MoveHeadDirJob(direction, 0)])

    def led(self, r: int, b: int, y: int, g: int) -> None:
        self._begin([LEDJob(r, b, y, g, 0)])

    def lcd(self, s: int, l: int) -> None:
        self._begin([LCDJob(s, l, 0)])

    def projector_mode(self, mode: int) -> None:
        self._begin([ProjectorJob(mode, 0)])

    def projector1(self) -> None:
        mode = 0 if self.state.projector == 1 else 1
        self._begin([ProjectorJob(mode, 0)])

    def projector2(self) -> None:
        mode = 0 if self.state.projector == 2 else 2
        self._begin([ProjectorJob(mode, 0)])

    def lightsaber(self, power: Optional[int] = None) -> None:
        if power is None:
            power = 0 if self.state.lightsaber else 1
        self._begin([ProjectorJob(0, 0), SoundJob(HAPPINESS_CONFIRMATION, 0), LightsaberJob(power, 0)])

    def arm(self, power: Optional[int] = None) -> None:
        if power is None:
            power = 0 if self.state.arm else 1
        self._begin([ProjectorJob(0, 0), SoundJob(HAPPINESS_CONFIRMATION, 0), HandJob(power, 0)])

    def shake_your_head(self) -> None:
        self._begin([
            SoundJob(HAPPY_THREE_CHIRP, 0),
            MoveHeadJob(-SHAKE_ANGLE, 600),
            MoveHeadJob(SHAKE_ANGLE, 600),
            MoveHeadJob(-SHAKE_ANGLE, 600),
            MoveHeadJob(SHAKE_ANGLE, 600),
            MoveHeadJob(0, 600),
        ])

    def voice_wake_up(self) -> None:
        self.led_controller.restore_all()
        self._begin([
            SoundJob(HAPPY_THREE_CHIRP, 0),
            MoveHeadJob(-NOD_ANGLE, 600),
            MoveHeadJob(NOD_ANGLE, 600),
            MoveHeadJob(0, 600),
        ])

    def end_voice(self) -> None:
        self.led_controller.restore_all()

    def who_are_you(self) -> None:
        self._begin([
            SoundJob(0, 0),
            MoveHeadJob(-NOD_ANGLE, 600),
            MoveHeadJob(NOD_ANGLE, 600),
            MoveHeadJob(0, 600),
        ])

    def not_recognize(self) -> None:
        self._begin([SoundJob(SHORT_RASPBERRY, 0)])

    def turn_around(self) -> None:
        self._begin([SoundJob(HAPPY_THREE_CHIRP, 0), ModeJob(MODE_TURN_AROUND, 700)])

    def turn_left(self) -> None:
        self._begin([SoundJob(HAPPY_THREE_CHIRP, 0), ModeJob(MODE_TURN_LEFT, 700)])

    def turn_right(self) -> None:
        self._begin([SoundJob(HAPPY_THREE_CHIRP, 0), ModeJob(MODE_TURN_RIGHT, 700)])

    def go_forward(self) -> None:
        self._begin([SoundJob(HAPPY_THREE_CHIRP, 0), ModeJob(MODE_GO_FORWARD, 700)])

    def walk_circle(self) -> None:
        self._begin([SoundJob(HAPPY_THREE_CHIRP, 0), ModeJob(MODE_WALK_CIRCLE, 700)])

    def mode_stop(self) -> None:
        self._begin([SoundJob(HAPPY_THREE_CHIRP, 0), ModeJob(MODE_STOP, 0)])

    def flash_font_lcd(self) -> None:
        self._begin([ModeJob(MODE_FLASH_FRONT_LCD, 0)])

    def flash_back_lcd(self) -> None:
        self._begin([ModeJob(MODE_FLASH_BACK_LCD, 0)])

    def angle_secret(self) -> None:
        self._begin([SoundJob(SOUND_ANGLE, 0)])

    def stark_secret(self) -> None:
        self._begin([SoundJob(SOUND_STARK, 0)])

    def dance(self) -> None:
        cycle = [
            SoundJob(SING_SONG_RESPONSE, 0),
            ModeJob(MODE_TURN_LEFT, 0),
            MoveHeadJob(-NOD_ANGLE, 600),
            ModeJob(MODE_TURN_AROUND, 600),
            MoveHeadJob(NOD_ANGLE, 600),
            ModeJob(MODE_TURN_LEFT, 600),
            MoveHeadJob(-NOD_ANGLE, 600),
            MoveHeadJob(NOD_ANGLE, 600),
            MoveHeadJob(0, 600),
            ModeJob(MODE_TURN_AROUND, 600),
        ]
        self._begin(cycle * 4)

    def short_lcd(self) -> None:
        # Both panels default to CLOSED; the short one is toggled and the long
        # one is re-asserted at whatever it currently is.
        s, l = LCD_CLOSED, LCD_CLOSED
        if not self.state.short_lcd:
            s = LCD_OPEN
        if self.state.long_lcd:
            l = LCD_OPEN
        self._begin([ProjectorJob(0, 0), SoundJob(HAPPINESS_CONFIRMATION, 0), LCDJob(s, l, 0)])

    def long_lcd(self) -> None:
        s, l = LCD_CLOSED, LCD_CLOSED
        if self.state.short_lcd:
            s = LCD_OPEN
        if not self.state.long_lcd:
            l = LCD_OPEN
        self._begin([ProjectorJob(0, 0), SoundJob(HAPPINESS_CONFIRMATION, 0), LCDJob(s, l, 0)])

    def patrol(self) -> None:
        current = self._current_mode()
        enable = current != APP_MODE_PATROL
        self._begin([
            ProjectorJob(0, 0),
            SoundJob(HAPPINESS_CONFIRMATION, 0),
            PatrolJob(enable, 0),
        ])

    # -- mode dispatcher ------------------------------------------------------
    def mode(self, mode: int) -> None:
        mode = int(mode)
        table: dict[int, Callable[[], None]] = {
            MODE_STOP: self.mode_stop,
            MODE_WAKE: self.voice_wake_up,
            MODE_TURN_AROUND: self.turn_around,
            MODE_TURN_LEFT: self.turn_left,
            MODE_TURN_RIGHT: self.turn_right,
            MODE_GO_FORWARD: self.go_forward,
            MODE_LIGHTSABER: lambda: self.lightsaber(),
            MODE_WHO_ARE_YOU: self.who_are_you,
            MODE_PATROL: self.patrol,
            MODE_DANCE: self.dance,
            MODE_WALK_CIRCLE: self.walk_circle,
            MODE_FLASH_FRONT_LCD: self.flash_font_lcd,
            MODE_FLASH_BACK_LCD: self.flash_back_lcd,
            MODE_SHAKE_HEAD: self.shake_your_head,
            MODE_HAND_JOB: lambda: self.arm(),
            MODE_SHORT_LCD: self.short_lcd,
            MODE_LONG_LCD: self.long_lcd,
            MODE_PROJECTOR_1: self.projector1,
            MODE_PROJECTOR_2: self.projector2,
        }
        handler = table.get(mode, self.not_recognize)
        handler()

    # -- non-queued notification events --------------------------------------
    def fail_in_pair_mode(self) -> None:
        self.sound_player.play_id(ALARMED_THRILL, False)
        self.led_controller.fail_in_pair_mode()

    def start_wifi_connection_event(self) -> None:
        self.sound_player.play_id(HAPPINESS_CONFIRMATION, False)
        self.led_controller.connect_wifi_mode()

    def user_grant_access_event(self) -> None:
        self.sound_player.play_id(LONELY_HELLO, False)
