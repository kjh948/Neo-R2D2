from __future__ import annotations

import threading
from enum import Enum
from typing import Dict, List, Optional

from .events import (
    MODE_DANCE,
    MODE_GO_FORWARD,
    MODE_NOT_RECOGNIZE,
    MODE_TURN_AROUND,
    MODE_TURN_LEFT,
    MODE_TURN_RIGHT,
    MODE_WHO_ARE_YOU,
)
from .log import get_logger

LOG = get_logger("voice")

LISTEN_TIMEOUT = 15.0


class VoiceCommand(Enum):
    """``VoiceToEventHandler.VoiceCommand``."""

    WAKE_UP = "wake_up"
    TURN_LEFT = "turn_left"
    TURN_RIGHT = "turn_right"
    TURN_AROUND = "turn_around"
    GO_FORWARD = "go_forward"
    SHAKE_HEAD = "shake_head"
    WALK_A_CIRCLE = "walk_a_circle"
    DANCE = "dance"
    MAKE_SOME_NOISE = "make_some_noise"
    WHO_ARE_YOU = "who_are_you"
    SKY_WALKER = "sky_walker"
    PRINCESS_LEIA = "princess_leia"
    LIGHT_SABER = "light_saber"
    ARMS = "arms"
    PATROL = "patrol"
    STOP = "stop"
    ANGLE_SECRET = "angle_secret"
    STARK_SECRET = "stark_secret"


# The phrase tables live in the Android resources (``R.array.voice_*``). The
# shipped APK forces a French locale (``MainApplication`` does
# ``new Locale("fr", "")``) and ``R.string.voice_path`` resolves to ``fr``, so
# the French entries below are what the device actually answers to; the English
# and Simplified-Chinese sets are what those locales ship, with ``en`` being the
# ``values/`` default.
#
# A phrase is reachable only if it appears in BOTH the array and the
# pocketsphinx ``keywords`` file. TURN_AROUND, MAKE_SOME_NOISE, STOP,
# SKY_WALKER, PRINCESS_LEIA, ANGLE_SECRET and STARK_SECRET have empty arrays in
# every locale, so those commands never fire from voice -- they are reachable
# only over the socket (``mode`` 2/6/0/19/20) or are entirely unwired.
VOICE_PHRASES: Dict[VoiceCommand, List[str]] = {
    VoiceCommand.WAKE_UP: [
        "salut", "salut r deux d deux", "bonjour",                                  # fr (live)
        "r two d two", "two d two", "hey r two d two", "good morning", "how are you",  # en
        "你 好",                                                                     # zh
    ],
    VoiceCommand.TURN_LEFT: [
        "tourne à gauche", "virage à gauche",
        "turn left", "left turn", "rotate left",
        "左 转", "看 左 方", "看 左 边",
    ],
    VoiceCommand.TURN_RIGHT: [
        "tourne à droite", "virage à droite",
        "turn right", "right turn", "rotate right",
        "右 转", "看 右 方", "看 右 边",
    ],
    VoiceCommand.TURN_AROUND: ["看 后面", "后 转", "看 后 方"],
    VoiceCommand.GO_FORWARD: [
        "avance tout droit", "va tout droit", "continue",
        "go forward", "go straight", "go ahead", "move forward",
        "向 前 走", "往 前 走", "走 向 前", "快 走",
    ],
    VoiceCommand.SHAKE_HEAD: [
        "secoue la tête", "dit non", "négatif",
        "shake your head", "say no", "negative",
        "摇 一下 头", "摇 一 摇 头", "转 一下 头", "转 一 转 头",
    ],
    VoiceCommand.WALK_A_CIRCLE: [
        "tourne en rond", "fais un cercle", "tourne tourne tourne",
        "walk a circle", "give me a circle", "round round round",
        "走 一 圈", "转 一 圈", "绕 一 圈",
    ],
    VoiceCommand.DANCE: [
        "danse s' il te plaît", "danse danse", "va danser",
        "dance now", "dancing dancing", "go dance", "dance please",
        "跳 舞", "跳 舞 吧", "一 起 跳 舞",
    ],
    VoiceCommand.MAKE_SOME_NOISE: ["说 一下", "讲 个 笑话", "你 说 什么"],
    VoiceCommand.WHO_ARE_YOU: ["qui es-tu", "who're you"],
    VoiceCommand.LIGHT_SABER: [
        "sabre laser", "actionnement sabre laser",
        "lightsaber", "lightsaber action",
        "拿出 激光剑", "看 看 激光剑", "拿出 武器", "看 看 武器",
        "收起 激光剑", "收起 武器",
    ],
    VoiceCommand.ARMS: [
        "bouge les bras", "bouge les connecteurs", "bras de liaison",
        "move arms", "arms", "spacecraft linkage",
        "打开 手臂", "伸出 手", "握 握 手", "收起 手 臂",
    ],
    VoiceCommand.PATROL: [
        "patrouille", "va patrouiller", "contrôle les alentours",
        "patrol", "go patrol", "check around",
        "去 巡逻", "看 好 这 边", "守住 这 边",
    ],
    VoiceCommand.STOP: ["停止", "休息 一下", "停 在 这 里"],
}


class VoiceToEventHandler:
    """Port of ``VoiceToEventHandler``: recognised phrase -> robot behaviour.

    ``getCommand`` keeps the original's prefix match (``voice.indexOf(phrase) ==
    0``) but iterates longest-phrase-first, because the app walks an unordered
    ``HashMap`` and so resolves overlapping phrases ("arms" vs "move arms")
    nondeterministically.
    """

    def __init__(self, events, mode_controller=None, phrases: Optional[Dict[VoiceCommand, List[str]]] = None) -> None:
        self.events = events
        self.mode_controller = mode_controller
        table: Dict[str, VoiceCommand] = {}
        for command, entries in (phrases or VOICE_PHRASES).items():
            for phrase in entries:
                phrase = phrase.strip()
                if phrase and phrase not in {"-", "－"}:
                    table[phrase.lower()] = command
        self._table = sorted(table.items(), key=lambda item: len(item[0]), reverse=True)

    def get_command(self, voice: str) -> Optional[VoiceCommand]:
        text = (voice or "").strip().lower()
        if not text:
            return None
        for phrase, command in self._table:
            if text.startswith(phrase):
                return command
        return None

    def voice_to_event(self, voice: str) -> bool:
        command = self.get_command(voice)
        found = command is not None
        if command is None:
            self.events.mode(MODE_NOT_RECOGNIZE)
        elif command == VoiceCommand.WAKE_UP:
            self.events.voice_wake_up()
        elif command == VoiceCommand.TURN_LEFT:
            self.events.mode(MODE_TURN_LEFT)
        elif command == VoiceCommand.TURN_RIGHT:
            self.events.mode(MODE_TURN_RIGHT)
        elif command == VoiceCommand.TURN_AROUND:
            self.events.mode(MODE_TURN_AROUND)
        elif command == VoiceCommand.GO_FORWARD:
            self.events.mode(MODE_GO_FORWARD)
        elif command == VoiceCommand.SHAKE_HEAD:
            self.events.shake_your_head()
        elif command == VoiceCommand.WALK_A_CIRCLE:
            self.events.walk_circle()
        elif command == VoiceCommand.DANCE:
            self.events.mode(MODE_DANCE)
        elif command == VoiceCommand.MAKE_SOME_NOISE:
            self.events.make_some_noise()
        elif command == VoiceCommand.WHO_ARE_YOU:
            self.events.mode(MODE_WHO_ARE_YOU)
        elif command == VoiceCommand.LIGHT_SABER:
            self.events.lightsaber()
        elif command == VoiceCommand.SKY_WALKER:
            self.events.mode(20)
        elif command == VoiceCommand.PRINCESS_LEIA:
            self.events.mode(19)
        elif command == VoiceCommand.ARMS:
            self.events.arm()
        elif command == VoiceCommand.PATROL:
            self.events.patrol()
        elif command == VoiceCommand.STOP:
            self.events.mode_stop()
        else:
            # ANGLE_SECRET / STARK_SECRET are recognised but intentionally do
            # nothing in the app; the easter eggs are triggered from the client.
            found = False
        if found and self.mode_controller is not None:
            self.mode_controller.wake()
        return True

    def end_voice_event(self) -> None:
        self.events.end_voice()


class VoiceRecognizer:
    """Port of ``VoiceRecognizer``'s three-state listening machine.

    PocketSphinx needs a model directory and a microphone, neither of which this
    host build assumes, so the decoder is pluggable and ``feed_keyword`` is the
    seam: tests and an external STT bridge push spotted phrases through the same
    path the on-device keyword search would.

    The state machine is what actually gates behaviour, so it is reproduced
    rather than simplified:

    * ``MODE_DISABLE`` (-1) — the initial value and the result of ``stop()``;
      phrases are dropped.
    * ``MODE_WAIT`` (0) — what ``start()`` enters. Only a wake-up phrase does
      anything here; any other phrase is **ignored** and re-arms waiting.
    * ``MODE_WAKE`` (1) — a wake word puts a 15 s window on the clock, and every
      phrase heard inside it executes and re-opens the window.

    When the window lapses the recognizer falls back to ``MODE_WAIT`` (it keeps
    listening) and fires ``endVoiceEvent()``, which restores the lights; it does
    **not** stop. Only sleep/patrol/pair stop it outright.
    """

    MODE_DISABLE = -1
    MODE_WAIT = 0
    MODE_WAKE = 1

    def __init__(self, handler: VoiceToEventHandler, timeout: float = LISTEN_TIMEOUT) -> None:
        self.handler = handler
        self.timeout = timeout
        self.current_mode = self.MODE_DISABLE
        self.is_voice_recognition_mode = False
        self._timer: Optional[threading.Timer] = None
        self._lock = threading.RLock()

    @property
    def is_active(self) -> bool:
        """True while the recognizer is listening in either WAIT or WAKE."""
        return self.current_mode != self.MODE_DISABLE

    # -- mode machine ---------------------------------------------------------
    def start(self) -> bool:
        with self._lock:
            if self.current_mode != self.MODE_DISABLE:
                LOG.debug("Voice Recognition started before")
                return False
            self._switch_mode(self.MODE_WAIT)
        LOG.info("Voice Recognizer start.")
        return True

    def stop(self) -> None:
        with self._lock:
            self._switch_mode(self.MODE_DISABLE)
            self.is_voice_recognition_mode = False
        # ``VoiceRecognizer.stop()`` also calls EventHandler.restoreLight(),
        # because the voice-session LED (back 202) would otherwise stick on.
        try:
            self.handler.events.restore_light()
        except Exception:  # pragma: no cover - mirrors the app's catch-free call
            LOG.exception("restore_light on stop failed")
        LOG.info("Voice Recognizer stop.")

    def _switch_mode(self, mode: int) -> None:
        """Caller must hold ``self._lock`` (or be inside a fresh timer)."""
        if self._timer is not None:
            self._timer.cancel()
            self._timer = None
        self.current_mode = mode
        LOG.debug("voice mode -> %d", mode)
        if mode == self.MODE_WAKE:
            timer = threading.Timer(self.timeout, self._on_timeout)
            timer.daemon = True
            self._timer = timer
            timer.start()

    def _on_timeout(self) -> None:
        with self._lock:
            if self.current_mode == self.MODE_DISABLE:
                return
            self._switch_mode(self.MODE_WAIT)
            self.is_voice_recognition_mode = False
        LOG.debug("MODE_WAKE TIMEOUT, switch to MODE_WAIT")
        self.handler.end_voice_event()

    # -- decoder seam ---------------------------------------------------------
    def feed_keyword(self, phrase: str) -> bool:
        """Deliver a spotted phrase; returns whether it drove a behaviour.

        Matches ``onRecognizeKeyword``: a phrase is only executed when the
        recognizer is already in ``MODE_WAKE``, or when the phrase itself is a
        wake-up word. Everything else re-arms waiting without acting.
        """
        with self._lock:
            mode = self.current_mode
            if mode == self.MODE_DISABLE:
                LOG.debug("ignoring %r: voice recognition disabled", phrase)
                return False
            command = self.handler.get_command(phrase)
            if mode == self.MODE_WAKE or command == VoiceCommand.WAKE_UP:
                self.is_voice_recognition_mode = True
                LOG.info("got keyword: %s", phrase)
                self.handler.voice_to_event(phrase)
                # ``voiceToEvent`` returns true on every path in the app, so the
                # window is always re-opened here.
                self._switch_mode(self.MODE_WAKE)
                return True
            LOG.info("ignoring %r while waiting for a wake word", phrase)
            self._switch_mode(self.MODE_WAIT)
            return False
