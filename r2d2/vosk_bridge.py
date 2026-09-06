from __future__ import annotations

import json
import shutil
import subprocess
import threading
from pathlib import Path
from typing import Optional

from .log import get_logger
from .voice import VoiceRecognizer

LOG = get_logger("vosk")

SAMPLE_RATE = 16000
CHUNK_SIZE = 4000

ENGLISH_PHRASES = (
    "r two d two",
    "two d two",
    "hey r two d two",
    "good morning",
    "turn left",
    "turn right",
    "go forward",
    "go straight",
    "shake your head",
    "walk a circle",
    "dance now",
    "lightsaber",
    "move arms",
    "patrol",
    "stop",
)

KOREAN_PHRASES = (
    "알투디투", "알 투 디 투", "좋은 아침",
    "왼쪽으로 돌아", "왼쪽 돌아", "오른쪽으로 돌아", "오른쪽 돌아",
    "앞으로 가", "직진", "고개 흔들어", "고개를 흔들어",
    "한 바퀴 돌아", "원을 그려", "춤춰", "춤을 춰",
    "너 누구야", "누구야", "광선검", "광선검 꺼내",
    "팔 움직여", "팔을 움직여", "순찰", "순찰해", "멈춰", "정지", "그만",
)


class VoskBridge:
    """Capture English microphone audio and feed recognized phrases to the app."""

    def __init__(
        self,
        voice_recognizer: VoiceRecognizer,
        model_path: str = "",
        korean_model_path: str = "",
        language: str = "en",
        audio_device: str = "default",
    ) -> None:
        self.voice_recognizer = voice_recognizer
        self.model_path = Path(model_path).expanduser()
        self.korean_model_path = Path(korean_model_path).expanduser() if korean_model_path else None
        self.language = language.lower().replace("_", "-")
        self.audio_device = audio_device
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._recorder: Optional[subprocess.Popen] = None
        self._recognizer = None

    def start(self) -> bool:
        if self._thread is not None and self._thread.is_alive():
            return True
        model_specs = self._model_specs()
        if not model_specs:
            LOG.error("no Vosk model configured for language=%s", self.language)
            return False
        missing = [path for path, _ in model_specs if not path.is_dir()]
        if missing:
            LOG.error("Vosk model directory not found: %s", ", ".join(map(str, missing)))
            return False
        try:
            self._recognizer = self._create_recognizer()
            self._recorder = self._start_recorder()
        except (ImportError, OSError, RuntimeError) as exc:
            LOG.error("Vosk voice recognition unavailable: %s", exc)
            self._recognizer = None
            self._recorder = None
            return False

        self._stop.clear()
        self.voice_recognizer.start()
        self._thread = threading.Thread(target=self._run, name="vosk", daemon=True)
        self._thread.start()
        LOG.info("Vosk voice recognition started: models=%s device=%s", ", ".join(str(path) for path, _ in model_specs), self.audio_device)
        return True

    def _model_specs(self):
        if self.language in {"ko", "korean"}:
            return [(self.korean_model_path, KOREAN_PHRASES)] if self.korean_model_path else []
        if self.language in {"en-ko", "ko-en", "bilingual", "english-korean", "korean-english"}:
            if not self.model_path or self.korean_model_path is None:
                return []
            return [
                (self.model_path, ENGLISH_PHRASES),
                (self.korean_model_path, KOREAN_PHRASES),
            ]
        if not self.model_path:
            return []
        return [(self.model_path, ENGLISH_PHRASES)]

    def stop(self) -> None:
        self._stop.set()
        recorder, self._recorder = self._recorder, None
        if recorder is not None and recorder.poll() is None:
            recorder.terminate()
        thread, self._thread = self._thread, None
        if thread is not None and thread.is_alive():
            thread.join(timeout=2.0)
        if recorder is not None:
            try:
                recorder.wait(timeout=1.0)
            except subprocess.TimeoutExpired:
                recorder.kill()
        self.voice_recognizer.stop()
        self._recognizer = None

    def _create_recognizer(self):
        try:
            from vosk import KaldiRecognizer, Model
        except ImportError as exc:
            raise ImportError("install Vosk with: python3 -m pip install vosk") from exc

        recognizers = []
        for path, phrases in self._model_specs():
            LOG.info("loading Vosk model: %s", path)
            model = Model(str(path))
            grammar = json.dumps(list(phrases) + ["[unk]"], ensure_ascii=False)
            recognizer = KaldiRecognizer(model, SAMPLE_RATE, grammar)
            recognizer.SetWords(True)
            recognizers.append(recognizer)
        return recognizers

    def _start_recorder(self) -> subprocess.Popen:
        arecord = shutil.which("arecord")
        if arecord is None:
            raise RuntimeError("arecord is not installed; install alsa-utils")
        command = [
            arecord,
            "-q",
            "-D",
            self.audio_device,
            "-f",
            "S16_LE",
            "-r",
            str(SAMPLE_RATE),
            "-c",
            "1",
            "-t",
            "raw",
        ]
        try:
            recorder = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                bufsize=0,
            )
        except OSError as exc:
            raise RuntimeError(f"failed to start arecord: {exc}") from exc
        LOG.debug("audio capture started: %s", " ".join(command))
        return recorder

    def _run(self) -> None:
        recorder = self._recorder
        recognizers = self._recognizer
        if recorder is None or recorder.stdout is None or not recognizers:
            return

        while not self._stop.is_set():
            audio = recorder.stdout.read(CHUNK_SIZE)
            if not audio:
                if recorder.poll() is not None and not self._stop.is_set():
                    error = recorder.stderr.read().decode(errors="replace").strip() if recorder.stderr else ""
                    LOG.error("arecord stopped with exit code %s: %s", recorder.returncode, error)
                return
            for recognizer in recognizers:
                if recognizer.AcceptWaveform(audio):
                    result = json.loads(recognizer.Result())
                    phrase = result.get("text", "").strip().lower()
                    if phrase:
                        LOG.info("Vosk recognized: %s", phrase)
                        self.voice_recognizer.feed_keyword(phrase)
