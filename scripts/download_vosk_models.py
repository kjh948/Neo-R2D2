#!/usr/bin/env python3
"""Download and unpack the Vosk models used by the R2D2 voice bridge."""

from __future__ import annotations

import argparse
import shutil
import ssl
import sys
import tempfile
from urllib.error import URLError
import urllib.request
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MODEL_DIR = REPO_ROOT / "r2d2" / "model"


def _https_context() -> ssl.SSLContext:
    try:
        import certifi
    except ImportError:
        return ssl.create_default_context()
    return ssl.create_default_context(cafile=certifi.where())

MODEL_SPECS = {
    "en": {
        "directory": "vosk-model-small-en-us-0.15",
        "url": "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
    },
    "ko": {
        "directory": "vosk-model-small-ko-0.22",
        "url": "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download and unpack Vosk models")
    parser.add_argument(
        "--language",
        choices=("en", "ko"),
        action="append",
        dest="languages",
        help="model to download; repeat for both languages (default: both)",
    )
    parser.add_argument(
        "--model-dir",
        type=Path,
        default=DEFAULT_MODEL_DIR,
        help="directory where the model directories are installed",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="replace an already unpacked model",
    )
    return parser.parse_args()


def _safe_extract(archive: zipfile.ZipFile, destination: Path) -> None:
    destination = destination.resolve()
    for member in archive.infolist():
        target = (destination / member.filename).resolve()
        if target != destination and destination not in target.parents:
            raise RuntimeError(f"unsafe path in archive: {member.filename}")
    archive.extractall(destination)


def download_model(language: str, model_dir: Path, force: bool) -> None:
    spec = MODEL_SPECS[language]
    model_name = spec["directory"]
    destination = model_dir / model_name
    if destination.is_dir() and not force:
        print(f"{language}: already exists, skipping {destination}")
        return

    model_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f"{model_name}-", dir=model_dir) as temp_dir:
        archive_path = Path(temp_dir) / f"{model_name}.zip"
        unpacked_dir = Path(temp_dir) / "unpacked"
        print(f"{language}: downloading {spec['url']}")
        request = urllib.request.Request(
            spec["url"],
            headers={"User-Agent": "Neo-R2D2 Vosk model downloader"},
        )
        try:
            with urllib.request.urlopen(request, context=_https_context()) as response, archive_path.open("wb") as output:
                shutil.copyfileobj(response, output)
        except (OSError, URLError) as exc:
            raise RuntimeError(f"download failed for {language}: {exc}") from exc

        unpacked_dir.mkdir()
        try:
            with zipfile.ZipFile(archive_path) as archive:
                _safe_extract(archive, unpacked_dir)
        except (OSError, zipfile.BadZipFile) as exc:
            raise RuntimeError(f"invalid ZIP for {language}: {exc}") from exc

        extracted_model = unpacked_dir / model_name
        if not extracted_model.is_dir():
            raise RuntimeError(f"ZIP does not contain {model_name}/")

        replacement = model_dir / f".{model_name}.new"
        if replacement.exists():
            shutil.rmtree(replacement)
        extracted_model.rename(replacement)
        if destination.exists():
            shutil.rmtree(destination)
        replacement.rename(destination)
        print(f"{language}: installed {destination}")


def main() -> int:
    args = parse_args()
    languages = args.languages or list(MODEL_SPECS)
    model_dir = args.model_dir.expanduser()
    try:
        for language in languages:
            download_model(language, model_dir, args.force)
    except (RuntimeError, URLError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
