"""Latency probe for the vint backend with RANDOM weights (compute-cost proxy).

Architecture/compute is identical to the real checkpoint, so these numbers are
valid for hardware sizing even before the Drive weights land. Run:

    venv/bin/python bench/vint_latency.py [--threads N]
"""

from __future__ import annotations

import argparse
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from navstack.config import NavConfig  # noqa: E402

REPO = Path(__file__).resolve().parents[1] / "visualnav-transformer"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--threads", type=int, default=0)
    ap.add_argument("--reps", type=int, default=7)
    args = ap.parse_args()

    import torch
    from vint_train.models.vint.vint import ViNT
    if args.threads:
        torch.set_num_threads(args.threads)

    model = ViNT(context_size=5, len_traj_pred=5, learn_angle=True,
                 obs_encoder="efficientnet-b0", obs_encoding_size=512,
                 late_fusion=False, mha_num_attention_heads=4,
                 mha_num_attention_layers=4, mha_ff_dim_factor=4)
    ck = Path("models/vint_weights/random_vint_bench.pth")
    ck.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"model": model}, ck)

    cfg = NavConfig(backend="vint", vint_config=str(REPO / "train/config/vint.yaml"),
                    vint_ckpt=str(ck), vint_threads=args.threads or 0)
    cfg.load()
    from navstack.vint_engine import VintEngine

    eng = VintEngine(cfg)
    frames = [np.random.randint(0, 255, (240, 320, 3), np.uint8)
              for _ in range(eng.context_size + 1)]
    goal = frames[0]
    subgoals = [np.random.randint(0, 255, (240, 320, 3), np.uint8)
                for _ in range(8)]

    def bench(label, goals):
        times = []
        for _ in range(args.reps):
            t0 = time.perf_counter()
            eng.infer(frames, goals)
            times.append((time.perf_counter() - t0) * 1000)
        print(f"{label:28s} median {statistics.median(times):7.0f} ms  "
              f"min {min(times):7.0f}  (threads={torch.get_num_threads()})")

    bench("1 goal (goal-image mode)", [goal])
    bench("8 subgoals (topomap window)", subgoals)
    # single-image ViT cost estimate
    t0 = time.perf_counter()
    for _ in range(10):
        eng._to_tensor(frames)
    print(f"preprocess 6 frames          median {(time.perf_counter()-t0)*100:.1f} ms")


if __name__ == "__main__":
    main()
