#!/usr/bin/env python3
"""Standalone Vosk microphone test for English speech recognition.

This script does not start the robot application or execute robot commands.
It only captures 16 kHz mono PCM from arecord and prints Vosk hypotheses.
"""

from __future__ import annotations

import argparse
import json
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

SAMPLE_RATE = 16000
CHUNK_SIZE = 4000

DEFAULT_PHRASES = [
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
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Test Vosk English recognition from an ALSA microphone")
    parser.add_argument(
        "--model",
        required=True,
        help="path to an unpacked Vosk English model",
    )
    parser.add_argument(
        "--device",
        default="default",
        help="arecord device, for example default or plughw:1,0",
    )
    parser.add_argument(
        "--seconds",
        type=float,
        default=0,
        help="stop after this many seconds; 0 means run until Ctrl-C",
    )
    parser.add_argument(
        "--free-speech",
        action="store_true",
        help="use unrestricted speech recognition instead of the command grammar",
    )
    parser.add_argument(
        "--partial",
        action="store_true",
        help="print partial hypotheses while speaking",
    )
    return parser.parse_args()


def load_vosk(model_path: Path, free_speech: bool):
    try:
        from vosk import KaldiRecognizer, Model
    except ImportError:
        print("Vosk is not installed. Run: python3 -m pip install vosk", file=sys.stderr)
        return None

    if not model_path.is_dir():
        print(f"Vosk model directory not found: {model_path}", file=sys.stderr)
        return None

    print(f"Loading Vosk model: {model_path}")
    model = Model(str(model_path))
    if free_speech:
        recognizer = KaldiRecognizer(model, SAMPLE_RATE)
    else:
        grammar = json.dumps(DEFAULT_PHRASES + ["[unk]"])
        recognizer = KaldiRecognizer(model, SAMPLE_RATE, grammar)
    recognizer.SetWords(True)
    return recognizer


def start_arecord(device: str) -> subprocess.Popen:
    arecord = shutil.which("arecord")
    if arecord is None:
        raise RuntimeError("arecord is not installed. Run: sudo apt install alsa-utils")

    command = [
        arecord,
        "-q",
        "-D",
        device,
        "-f",
        "S16_LE",
        "-r",
        str(SAMPLE_RATE),
        "-c",
        "1",
        "-t",
        "raw",
    ]
    print("Starting audio capture:", " ".join(command))
    return subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0,
    )


def main() -> int:
    args = parse_args()
    recognizer = load_vosk(Path(args.model).expanduser(), args.free_speech)
    if recognizer is None:
        return 2

    try:
        recorder = start_arecord(args.device)
    except (OSError, RuntimeError) as exc:
        print(f"Audio capture failed: {exc}", file=sys.stderr)
        return 2

    stopping = False

    def stop(_signum=None, _frame=None) -> None:
        nonlocal stopping
        stopping = True
        if recorder.poll() is None:
            recorder.terminate()

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    print("Listening. Speak an English command; press Ctrl-C to stop.")
    if not args.free_speech:
        print("Grammar mode: known robot phrases only")
    print("-" * 60)

    started = time.monotonic()
    try:
        if recorder.stdout is None:
            print("arecord did not provide an audio stream", file=sys.stderr)
            return 2
        while not stopping:
            if args.seconds > 0 and time.monotonic() - started >= args.seconds:
                break
            audio = recorder.stdout.read(CHUNK_SIZE)
            if not audio:
                if recorder.poll() is not None:
                    error = recorder.stderr.read().decode(errors="replace").strip() if recorder.stderr else ""
                    print(f"arecord stopped with exit code {recorder.returncode}: {error}", file=sys.stderr)
                    return 1
                continue

            if recognizer.AcceptWaveform(audio):
                result = json.loads(recognizer.Result())
                text = result.get("text", "").strip()
                if text:
                    print(f"FINAL:   {text}", flush=True)
            elif args.partial:
                result = json.loads(recognizer.PartialResult())
                text = result.get("partial", "").strip()
                if text:
                    print(f"PARTIAL: {text}", flush=True)
    finally:
        stop()
        if recorder.stdout is not None:
            recorder.stdout.close()
        if recorder.stderr is not None:
            recorder.stderr.close()
        recorder.wait(timeout=2)

    print("Vosk microphone test stopped.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
