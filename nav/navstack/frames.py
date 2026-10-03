"""Image helpers shared by the navigator and the vint engine.

Frames travel the wire as JPEG bytes; everything downstream works on HWC
uint8 RGB numpy arrays. Resize/normalization for the model itself lives in
VintEngine (it must match the training pipeline exactly).
"""

from __future__ import annotations

import base64
import io

import numpy as np
from PIL import Image


def decode_jpeg(data: bytes | bytearray) -> np.ndarray:
    """JPEG/PNG bytes -> HWC uint8 RGB ndarray."""
    img = Image.open(io.BytesIO(bytes(data))).convert("RGB")
    return np.asarray(img, dtype=np.uint8)


def decode_b64(b64: str) -> np.ndarray:
    return decode_jpeg(base64.b64decode(b64))


def encode_jpeg(frame: np.ndarray, quality: int = 90) -> bytes:
    buf = io.BytesIO()
    Image.fromarray(frame).save(buf, format="JPEG", quality=quality)
    return buf.getvalue()
