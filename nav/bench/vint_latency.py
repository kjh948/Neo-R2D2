"""Latency probe for the vint backend models (goal-image and NoMaD explore).

Usage: venv/bin/python bench/vint_latency.py [config.yaml ckpt.pth ...]
With no args, benchmarks every model whose checkpoint is present under
models/vint_weights/checkpoints.
"""

from __future__ import annotations

import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from navstack.config import NavConfig  # noqa: E402
from navstack.vint_engine import VintEngine, VintSession  # noqa: E402

REPO = Path("visualnav-transformer")
CKPT_DIR = Path("models/vint_weights/checkpoints")
PAIRS = [("vint.yaml", "vint.pth"), ("gnm.yaml", "gnm.pth"),
         ("nomad.yaml", "nomad.pth")]


def bench(name, cfg_kwargs):
    cfg = NavConfig(**cfg_kwargs).load()
    eng = VintEngine(cfg)
    w, h = eng.image_size
    frames = [np.random.randint(0, 255, (h * 3, w * 3, 3), np.uint8)
              for _ in range(eng.context_size + 1)]
    ses = VintSession(eng)
    for f in frames:
        ses.observe(f)
    times = []
    for _ in range(9):
        if eng.model_type != "nomad":
            ses.goal_img = frames[-1]
        t0 = time.perf_counter()
        ses.predict()
        times.append((time.perf_counter() - t0) * 1000)
    print(f"{name:34s} median {statistics.median(times):7.0f} ms  "
          f"min {min(times):7.0f}  threads={__import__('torch').get_num_threads()}")


def main() -> None:
    args = sys.argv[1:]
    if args:
        bench(Path(args[1]).stem, {"vint_config": args[0], "vint_ckpt": args[1]})
        return
    for yaml_name, pth in PAIRS:
        cfg, ck = REPO / "train/config" / yaml_name, CKPT_DIR / pth
        if cfg.is_file() and ck.is_file():
            bench(pth, {"vint_config": str(cfg), "vint_ckpt": str(ck)})


if __name__ == "__main__":
    main()
