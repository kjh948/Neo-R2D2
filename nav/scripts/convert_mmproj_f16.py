"""Convert a bf16 mmproj GGUF to f16 (CPU performance on x86).

ggml's CPU path for BF16 tensors is drastically slower than F16 (software
bit conversion per tile; x86 has no native bf16), which on this Intel host
made the Qwen3-VL vision tower take ~40 s per frame. bf16 -> f16 is lossless
for the ViT's numeric range and is how llama.cpp ships mmprojs normally.

Raw GGUF rewrite: header + KV metadata copied verbatim, tensor info entries
rewritten with dtype F16 where it was BF16, data converted elementwise
(uint16 << 16 -> float32 -> float16), data re-padded to the file's alignment.
"""

from __future__ import annotations

import struct
import sys
from pathlib import Path

import numpy as np

# GGUF metadata value types
T_UINT32, T_INT32, T_FLOAT32, T_BOOL, T_STRING, T_ARRAY = 4, 5, 6, 7, 8, 9
T_UINT64, T_INT64, T_FLOAT64, T_UINT8, T_INT8, T_UINT16, T_INT16 = 10, 11, 12, 0, 1, 2, 3
# ggml tensor dtypes as stored in GGUF tensor info (full ggml enum indices)
G_F32, G_F16, G_BF16 = 0, 1, 30
ELEM = {G_F32: 4, G_F16: 2, G_BF16: 2}


def _read_string(f) -> bytes:
    (n,) = struct.unpack("<Q", f.read(8))
    return f.read(n)


def _skip_value(f, vtype: int) -> None:
    if vtype in (T_UINT8, T_INT8, T_BOOL):
        f.read(1)
    elif vtype in (T_UINT16, T_INT16):
        f.read(2)
    elif vtype in (T_UINT32, T_INT32, T_FLOAT32):
        f.read(4)
    elif vtype in (T_UINT64, T_INT64, T_FLOAT64):
        f.read(8)
    elif vtype == T_STRING:
        _read_string(f)
    elif vtype == T_ARRAY:
        (atype,) = struct.unpack("<I", f.read(4))
        (n,) = struct.unpack("<Q", f.read(8))
        for _ in range(n):
            _skip_value(f, atype)
    else:
        raise NotImplementedError(f"kv value type {vtype}")


def _pad_up(x: int, align: int) -> int:
    return (x + align - 1) // align * align


def convert(src: Path, dst: Path) -> None:
    # ---- read ------------------------------------------------------------
    with src.open("rb") as f:
        assert f.read(4) == b"GGUF"
        (version,) = struct.unpack("<I", f.read(4))
        (n_tensors,) = struct.unpack("<Q", f.read(8))
        (n_kv,) = struct.unpack("<Q", f.read(8))

        kv_start = f.tell()
        align, end = _scan_kv(f, n_kv)
        f.seek(kv_start)
        kv_bytes = f.read(end - kv_start)

        infos = []  # (name, shape, dtype)
        for _ in range(n_tensors):
            name = _read_string(f)
            (nd,) = struct.unpack("<I", f.read(4))
            shape = [struct.unpack("<Q", f.read(8))[0] for _ in range(nd)]
            (dtype,) = struct.unpack("<I", f.read(4))
            f.read(8)  # old offset (data_start-relative); recomputed below
            infos.append((name, shape, dtype))

        f.seek(_pad_up(f.tell(), align))
        payloads = []
        for _name, shape, dtype in infos:
            n = int(np.prod(shape)) if shape else 1
            size = n * ELEM[dtype]
            chunk = f.read(size)
            payloads.append(chunk)
            f.read(-f.tell() % align)  # skip padding to next aligned tensor

    # ---- bf16 -> f16 -------------------------------------------------------
    out = []
    for (name, shape, dtype), chunk in zip(infos, payloads):
        if dtype == G_BF16:
            bits = np.frombuffer(chunk, dtype=np.uint16)
            f32 = (bits.astype(np.uint32) << 16).view(np.float32)
            chunk = np.ascontiguousarray(f32.astype(np.float16)).tobytes()
            dtype = G_F16
        out.append((name, shape, dtype, chunk))

    # ---- write -------------------------------------------------------------
    with dst.open("wb") as g:
        g.write(b"GGUF")
        g.write(struct.pack("<I", version))
        g.write(struct.pack("<Q", n_tensors))
        g.write(struct.pack("<Q", n_kv))
        g.write(kv_bytes)
        # offsets: tensor i at aligned cumulative position from data start
        sizes = [len(t[3]) for t in out]
        offsets, acc = [], 0
        for s in sizes:
            offsets.append(acc)
            acc = _pad_up(acc + s, align)
        for (name, shape, dtype, _chunk), o in zip(out, offsets):
            g.write(struct.pack("<Q", len(name)) + name)
            g.write(struct.pack("<I", len(shape)))
            for d in shape:
                g.write(struct.pack("<Q", d))
            g.write(struct.pack("<I", dtype))
            g.write(struct.pack("<Q", o))
        g.write(b"\x00" * (-g.tell() % align))
        for i, (_n, _s, _d, chunk) in enumerate(out):
            g.write(chunk)
            if i != len(out) - 1:
                g.write(b"\x00" * (-g.tell() % align))
    n_f16 = sum(1 for t in out if t[2] == G_F16)
    n_f32 = sum(1 for t in out if t[2] == G_F32)
    print(f"wrote {dst} ({dst.stat().st_size / 1e6:.0f} MB; {n_f16} f16, {n_f32} f32 tensors)")


def _scan_kv(f, n_kv: int) -> tuple[int, int]:
    """Walk the KV metadata block once; return (alignment, byte offset after it)."""
    align = 32
    for _ in range(n_kv):
        key = _read_string(f)
        (vtype,) = struct.unpack("<I", f.read(4))
        if key == b"general.alignment" and vtype == T_UINT32:
            (align,) = struct.unpack("<I", f.read(4))
            continue
        _skip_value(f, vtype)
    return align, f.tell()


if __name__ == "__main__":
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "models/LightNav-0.mmproj-bf16.gguf")
    dst = Path(sys.argv[2] if len(sys.argv) > 2 else "models/LightNav-0.mmproj-f16.gguf")
    convert(src, dst)
