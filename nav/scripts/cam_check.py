#!/usr/bin/env python3
"""Camera diagnostics: what the camera sends vs what the model actually sees.

Run from YOUR terminal (camera permission prompts only work interactively):

    venv/bin/python scripts/cam_check.py [--camera usb:0] [--frames 3]

Writes to cam_check_out/:
  raw_<i>.jpg          frames straight from the source (should look normal)
  model_<i>.upscaled.png  4:3 crop -> model resize -> IMAGENET denorm, 4x zoom
                          (this is literally the pixels the policy consumes;
                           squashed/garbled here = preprocessing bug)
prints per-frame stats: size, per-channel means (black frame? channel swap?)
"""

from __future__ import annotations

import argparse
import asyncio
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

OUT = Path("cam_check_out")


def stats(name: str, arr: np.ndarray) -> None:
    b, g, r = (float(arr[..., i].mean()) for i in range(3))
    print(f"{name:28s} {arr.shape[1]}x{arr.shape[0]} "
          f"mean B={b:5.1f} G={g:5.1f} R={r:5.1f} min={arr.min()} max={arr.max()}")


def model_view(jpeg: bytes, cfg, engine_kind: str) -> np.ndarray:
    """Recreate the exact tensor pipeline then undo normalization for viewing."""
    from navstack.frames import decode_jpeg
    img = Image.fromarray(decode_jpeg(jpeg)).convert("RGB")
    from navstack.vint_engine import VintEngine
    img = VintEngine._aspect_crop(img)
    w, h = cfg
    small = img.resize((w, h), Image.BILINEAR)
    arr = np.asarray(small, dtype=np.float32) / 255.0
    mean = np.array([0.485, 0.456, 0.406], np.float32)
    std = np.array([0.229, 0.224, 0.225], np.float32)
    denorm = (arr - mean) / std * std + mean      # normalize -> view-normalize
    out = (np.clip(denorm, 0, 1) * 255).astype(np.uint8)
    return np.asarray(Image.fromarray(out).resize((w * 4, h * 4), Image.LANCZOS))


async def grab(spec: str, n_frames: int):
    from navstack.camera import make_source
    src = make_source(spec)
    await src.connect()
    frames = []
    t0 = time.monotonic()
    while len(frames) < n_frames and time.monotonic() - t0 < 15:
        j = await src.get_jpeg()
        if j and (not frames or j != frames[-1]):
            frames.append(j)
        await asyncio.sleep(0.1)
    await src.close()
    return frames


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--camera", default="usb:0")
    ap.add_argument("--frames", type=int, default=3)
    ap.add_argument("--image-size", default="96,96",
                    help="model input W,H (nomad/vint default 96,96)")
    args = ap.parse_args()

    OUT.mkdir(exist_ok=True)
    frames = asyncio.run(grab(args.camera, args.frames))
    if not frames:
        print("NO FRAMES — camera unavailable (check permission/index). "
              "Try: --camera usb:1")
        return
    cfg = tuple(int(v) for v in args.image_size.split(","))
    for i, j in enumerate(frames):
        raw = np.asarray(Image.open(__import__("io").BytesIO(j)).convert("RGB"))
        stats(f"raw_{i}", raw)
        Image.fromarray(raw).save(OUT / f"raw_{i}.jpg")
        mv = model_view(j, cfg, "")
        stats(f"model_{i}", mv)
        Image.fromarray(mv).save(OUT / f"model_{i}.upscaled.png")
    print(f"\nwrote {len(frames)}x(raw, model) to {OUT}/ — "
          "black raw => TCC/permission; stretched model => preprocess; "
          "odd colors => BGR/RGB swap")


if __name__ == "__main__":
    main()
