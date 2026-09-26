"""Frame decoding, resizing and per-tier downscaling (the pixel-side of pooling).

The trained contract (lightnav.inference.frame_preprocessing) resizes every
frame to ``video_size`` [H, W] with bilinear interpolation and feeds the ViT
``uint8/255*2-1`` -- i.e. mean/std normalization with mean=std=0.5, which is
exactly what llama.cpp's mtmd Qwen-VL preprocessing applies. So feeding
correctly-resized JPEGs to llama-server keeps the pixel space faithful.

Post-ViT spatial pooling by factor p (the SlowFast tiers' ``pool_spatial``)
averages p×p blocks of vision tokens. llama.cpp cannot pool tokens inside its
graph, so we approximate: history frames are downscaled p× before encoding.
Token counts per frame then match the trained layout exactly (grid shrinks by
p), at the cost of pooling in pixel space instead of embedding space.
"""

from __future__ import annotations

import base64
import io
from typing import Tuple

import numpy as np
from PIL import Image

Frame = Tuple[int, int]  # (H, W)


def decode_jpeg(data: bytes | bytearray) -> np.ndarray:
    """JPEG/PNG bytes -> HWC uint8 RGB ndarray (any source resolution)."""
    img = Image.open(io.BytesIO(bytes(data))).convert("RGB")
    return np.asarray(img, dtype=np.uint8)


def decode_b64(b64: str) -> np.ndarray:
    return decode_jpeg(base64.b64decode(b64))


def stretch_resize(frame: np.ndarray, size_hw: Frame) -> np.ndarray:
    """HWC uint8 -> resize to (H, W), ignoring aspect (the trained 'stretch' mode)."""
    h, w = size_hw
    if frame.shape[0] == h and frame.shape[1] == w:
        return frame
    img = Image.fromarray(frame).resize((w, h), Image.BILINEAR)
    return np.asarray(img, dtype=np.uint8)


def encode_jpeg(frame: np.ndarray, quality: int = 90) -> bytes:
    return _jpeg_bytes(frame, quality)


def jpeg_data_url(frame: np.ndarray, quality: int = 90) -> str:
    return "data:image/jpeg;base64," + base64.b64encode(_jpeg_bytes(frame, quality)).decode()


def tier_frames(frame_hw: np.ndarray, video_size: Frame, pool_spatial: int) -> np.ndarray:
    """One buffered frame -> the frame actually sent for a tier of given pooling.

    current tier (pool 1) -> video_size; pool 2 -> video_size/2, etc. Both sides
    stay multiples of 32 for the released 256x448 contract (256/2=128, 448/2=224,
    /4 -> 64x112: all patch*merge multiples), so token grids match training.
    """
    base = stretch_resize(frame_hw, video_size)
    if pool_spatial <= 1:
        return base
    h, w = video_size
    return stretch_resize(base, (max(h // pool_spatial, 32), max(w // pool_spatial, 32)))


def _jpeg_bytes(frame: np.ndarray, quality: int) -> bytes:
    buf = io.BytesIO()
    Image.fromarray(frame).save(buf, format="JPEG", quality=quality)
    return buf.getvalue()
