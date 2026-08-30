from __future__ import annotations

import os
import shutil
import subprocess
import threading
from typing import Callable, Optional

from .log import get_logger

LOG = get_logger("sound")

_PLAYER_CANDIDATES = (
    ("paplay", ("--volume", "65536")),
    ("aplay", ()),
    ("ffplay", ("-nodisp", "-autoexit", "-loglevel", "quiet")),
    ("afplay", ()),
)

# ``SoundPlayer.getSoundEffectRawId`` translated from R.raw ids to the clip
# names shipped in ``sound_effects/``. The robot references sounds by numeric
# id end-to-end (wire ``sound_id`` -> ``SoundJob`` -> this table -> file).
SOUND_EFFECTS: Dict[int, str] = {
    0: "pulling_it_together",
    1: "sing_song_response",
    2: "abrupt_burst",
    3: "alarmed_thrill",
    4: "building_freak_out",
    5: "curt_reply",
    6: "danger_danger",
    7: "happiness_confirmation",
    8: "happy_three_chirp",
    9: "lonely_hello",
    10: "lonely_singing",
    11: "nagging_whine",
    12: "short_raspberry",
    13: "startled_three_tone",
    14: "startled_whoop",
    15: "stifled_laugh",
    16: "uncertain_two_tone",
    17: "unconvinced_grumbling",
    18: "upset_two_tone",
    100: "starwar01_right_02",
    101: "starwar03_right_02",
    301: "angle",
    302: "stark",
}

# ``default:`` branch of the switch in the original.
SOUND_FALLBACK = "happy_three_chirp"


def sound_name_for_id(sound_id: int) -> str:
    return SOUND_EFFECTS.get(int(sound_id), SOUND_FALLBACK)


# Named ids from SoundPlayer.java, kept so behaviour code can read the same.
PULLING_IT_TOGETHER = 0
SING_SONG_RESPONSE = 1
ABRUPT_THRILL = 2
ALARMED_THRILL = 3
BUILDING_FREAK_OUT = 4
CURT_REPLY = 5
DANGER_DANGER = 6
HAPPINESS_CONFIRMATION = 7
HAPPY_THREE_CHIRP = 8
LONELY_HELLO = 9
LONELY_SINGING = 10
NAGGING_WHINE = 11
SHORT_RASPBERRY = 12
STARTLED_THREE_TONE = 13
STARTLED_WHOOP = 14
STIFLED_LAUGH = 15
UNCERTAIN_TWO_TONE = 16
UNCONVINCED_GRUMBLING = 17
UPSET_TWO_TONE = 18
STARWAR01 = 100
STARWAR03 = 101
SOUND_ANGLE = 301
SOUND_STARK = 302


def _wav_only(player: str) -> bool:
    return player in {"paplay", "aplay"}


class SoundPlayer:
    """Plays a sound file on the host, mirroring ``SoundPlayer`` in the app.

    Only one clip plays at a time. ``interrupt=True`` cuts the current clip,
    which is how the MCU-driven startle/alert sounds behave. A background
    watcher reports completion so callers can chain animations to audio, and a
    mock mode keeps the port testable without an audio device.
    """

    def __init__(
        self,
        sound_dir: str,
        volume: int = 100,
        mute: bool = False,
        mock: bool = False,
    ) -> None:
        self.sound_dir = sound_dir
        self.volume = max(0, min(100, volume))
        self.muted = mute
        self.mock = mock
        # Filled in mock mode so tests and ``--mock`` runs can see what would
        # have been audible without an audio device.
        self.played: list[str] = []
        self._lock = threading.Lock()
        self._process: Optional[subprocess.Popen] = None
        self._current: Optional[str] = None
        self._completion: Optional[threading.Event] = threading.Event()
        self._completion.set()
        self._stop = threading.Event()
        self._watcher: Optional[threading.Thread] = None

    def resolve(self, name: str) -> Optional[str]:
        if os.path.isabs(name) and os.path.isfile(name):
            return name
        for ext in ("", ".mp3", ".wav", ".ogg"):
            candidate = os.path.join(self.sound_dir, name + ext)
            if os.path.isfile(candidate):
                return candidate
        return None

    def play(
        self,
        name: str,
        interrupt: bool = False,
        on_finished: Optional[callable] = None,
    ) -> bool:
        if self.muted:
            LOG.debug("muted, skipping %s", name)
            return False
        path = self.resolve(name)
        if path is None:
            LOG.warning("sound file not found: %s", name)
            return False
        with self._lock:
            if self._process is not None and self._process.poll() is None:
                # The app's ``isPlaying`` flag is never set, so every play()
                # clobbers the running clip regardless of ``interrupt``; a new
                # request always wins.
                if not interrupt:
                    LOG.debug("replacing %s with %s", self._current, name)
                self.stop()
            if self.mock:
                LOG.info("mock play: %s", os.path.basename(path))
                self.played.append(os.path.splitext(os.path.basename(path))[0])
                self._current = os.path.basename(path)
                self._completion = threading.Event()
                if on_finished is not None:
                    timer = threading.Timer(0.01, on_finished)
                    timer.daemon = True
                    timer.start()
                return True
            argv = self._argv_for(path)
            if argv is None:
                LOG.error("no audio player available (install one of: %s)",
                          ", ".join(p for p, _ in _PLAYER_CANDIDATES))
                return False
            try:
                self._process = subprocess.Popen(
                    argv,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    stdin=subprocess.DEVNULL,
                )
            except OSError as exc:
                LOG.error("failed to start %s: %s", argv[0], exc)
                return False
            self._current = os.path.basename(path)
            self._completion = threading.Event()
            self._watch(path, self._process, on_finished)
            return True

    def play_id(self, sound_id: int, interrupt: bool = True, on_finished: Optional[Callable[[], None]] = None) -> bool:
        """Play clip ``sound_id`` from the app's numeric sound table."""
        return self.play(sound_name_for_id(sound_id), interrupt, on_finished)

    def pause(self) -> bool:
        """Pause playback, as ``SoundPlayer.pause()`` does when a projector is
        switched off. Returns ``False`` when nothing is loaded, because the app
        leaves ``MediaPlayer`` paused-but-released in that state."""
        with self._lock:
            process, self._process = self._process, None
            self._current = None
        if process is None or process.poll() is not None:
            return False
        process.terminate()
        if self._completion is not None:
            self._completion.set()
        return True

    def _argv_for(self, path: str) -> Optional[list[str]]:
        is_wav = path.lower().endswith(".wav")
        for player, flags in _PLAYER_CANDIDATES:
            if _wav_only(player) and not is_wav:
                continue
            binary = shutil.which(player)
            if binary is None:
                continue
            if player == "ffplay":
                volume_arg = ["-volume", str(int(self.volume / 100 * 100))]
                return [binary, *flags, *volume_arg, path]
            if player == "paplay" and self.volume != 100:
                return [binary, "--volume", str(int(self.volume * 655)), path]
            return [binary, *flags, path]
        return None

    def _watch(self, path: str, process: subprocess.Popen, on_finished) -> None:
        def monitor() -> None:
            try:
                process.wait(timeout=120)
            except subprocess.TimeoutExpired:
                process.kill()
            finally:
                with self._lock:
                    if self._process is process:
                        self._process = None
                        self._current = None
                self._completion.set()
                if on_finished is not None:
                    try:
                        on_finished()
                    except Exception:  # pragma: no cover
                        LOG.exception("sound completion callback raised")

        thread = threading.Thread(target=monitor, name="sound-watch", daemon=True)
        self._watcher = thread
        thread.start()

    def stop(self) -> None:
        with self._lock:
            process, self._process = self._process, None
            self._current = None
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=1.0)
            except subprocess.TimeoutExpired:
                process.kill()
        if self._completion is not None:
            self._completion.set()

    def wait(self, timeout: Optional[float] = None) -> bool:
        event = self._completion
        return event.wait(timeout) if event is not None else True

    @property
    def is_playing(self) -> bool:
        with self._lock:
            process = self._process
        return process is not None and process.poll() is None

    def set_muted(self, muted: bool) -> None:
        self.muted = bool(muted)
        if muted:
            self.stop()

    def set_volume(self, volume: int) -> None:
        self.volume = max(0, min(100, int(volume)))

    def close(self) -> None:
        self._stop.set()
        self.stop()
