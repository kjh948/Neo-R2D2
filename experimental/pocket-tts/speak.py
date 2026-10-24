#!/usr/bin/env python3
"""Generate Korean speech with Pocket-TTS and stream it to the speakers."""

import argparse
from pathlib import Path

import sounddevice as sd
from pocket_tts import TTSModel


PROJECT_DIR = Path(__file__).resolve().parent


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate speech from text with Pocket-TTS and play it directly."
    )
    parser.add_argument(
        "text",
        nargs="*",
        help="Text to speak. If omitted, enter the text at the prompt.",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=PROJECT_DIR / "korean.yaml",
        help="Pocket-TTS model config (default: korean.yaml)",
    )
    parser.add_argument(
        "--voice",
        type=Path,
        default=PROJECT_DIR / "prompt.wav",
        help="Voice prompt WAV (default: prompt.wav)",
    )
    args = parser.parse_args()

    text = " ".join(args.text).strip() if args.text else input("읽을 문장을 입력하세요: ").strip()
    if not text:
        parser.error("읽을 문장을 입력해야 합니다.")

    args.text = text
    for option_name in ("config", "voice"):
        path = getattr(args, option_name)
        if not path.is_file():
            parser.error(f"{option_name} 파일을 찾을 수 없습니다: {path}")
    return args


def main() -> None:
    args = parse_args()
    model = TTSModel.load_model(config=args.config.resolve())
    voice_state = model.get_state_for_audio_prompt(args.voice.resolve())

    with sd.OutputStream(samplerate=model.sample_rate, channels=1, dtype="float32") as stream:
        for chunk in model.generate_audio_stream(voice_state, args.text):
            audio = chunk.detach().cpu().numpy().astype("float32", copy=False)
            stream.write(audio.reshape(-1, 1))


if __name__ == "__main__":
    main()
