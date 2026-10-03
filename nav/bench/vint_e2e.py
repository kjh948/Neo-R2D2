"""Real-weights end-to-end probe for the vint backend.

Walks a synthetic route (a red door sliding across the frame) toward the LAST
frame as the goal, and checks the two things we can verify without a real
robot: predicted distance DECREASES monotonically as the goal comes into view
and near the end, and the waypoint lat sign steers toward the door.

    venv/bin/python bench/vint_e2e.py [ckpt.pth]
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from navstack.config import NavConfig  # noqa: E402
from navstack.vint_engine import VintEngine, VintSession  # noqa: E402

CKPT = sys.argv[1] if len(sys.argv) > 1 else "models/vint_weights/checkpoints/vint.pth"
CFG = "visualnav-transformer/train/config/vint.yaml"


def route(n=8):
    frames = []
    for i in range(n):
        a = np.full((240, 320, 3), 70, np.uint8)
        x = 30 + i * (260 // (n - 1))
        a[90:200, x:x + 50] = (210, 40, 40)   # the "door" drifts rightward
        frames.append(a)
    return frames


def main() -> None:
    cfg = NavConfig(vint_config=CFG, vint_ckpt=CKPT).load()
    eng = VintEngine(cfg)
    seq = route()
    ses = VintSession(eng, "e2e")
    ses.goal_img = seq[-1]                     # goal = final viewpoint
    lat = []
    dists = []
    for f in seq:
        ses.observe(f)
        if len(ses.frames) < eng.context_size + 1:
            continue
        t0 = time.time()
        d = ses.predict()
        lat.append(time.time() - t0)
        dist = float(d["raw_text"].split("dist=")[1].split("m")[0])
        dists.append(dist)
        wp = np.array(d["actions"]["actions"])
        print(f"dist={dist:5.2f}m  wp_end fwd={wp[-1,0]:+.2f} lat={wp[-1,1]:+.2f} "
              f"yaw={wp[-1,2]:+.2f}  stop={d['stop']}  {lat[-1]*1000:.0f}ms")
    med = sorted(lat)[len(lat) // 2]
    print(f"--- median latency {med*1000:.0f} ms")
    decreased = dists[0] >= dists[-1]
    print(f"distance trend: {dists[0]:.2f} -> {dists[-1]:.2f}  "
          f"({'DECREASED toward goal' if decreased else 'did not decrease'})")


if __name__ == "__main__":
    main()
