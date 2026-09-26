#!/usr/bin/env python3
"""Collect a topological map (ordered 0.jpg..N.jpg) WITHOUT ROS.

The visualnav-transformer deployment builds topomaps from rosbags; R2D2 has no
ROS. This script samples the robot's live video stream (r2d2 :12121, binary
JPEG at 10 fps, single viewer) every --dt seconds while you teleop the robot
along the route you want it to be able to navigate. Stop with Ctrl-C.

  python scripts/record_topomap.py --robot <pi-ip> --dt 1.0 out_dir/
  python scripts/record_topomap.py --dir frames_in/ --dt auto out_dir/   # retime a dir

out_dir then plugs straight into:
  python -m navstack serve --backend vint ... --topomap out_dir
  (or drive --topomap out_dir)
"""

from __future__ import annotations

import argparse
import asyncio
import shutil
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


async def record_stream(robot: str, port: int, out: Path, dt: float) -> int:
    from websockets.asyncio.client import connect
    n = 0
    out.mkdir(parents=True, exist_ok=True)
    async with connect(f"ws://{robot}:{port}/", max_size=2 ** 22) as ws:
        hello = await ws.recv()
        print("stream hello:", hello)
        next_save = 0.0
        while True:
            frame = await ws.recv()
            if not isinstance(frame, (bytes, bytearray)):
                continue
            now = time.monotonic()
            if now < next_save:
                continue
            next_save = now + dt
            path = out / f"{n}.jpg"
            path.write_bytes(bytes(frame))
            n += 1
            print(f"saved {path} ({len(frame)//1024} KiB)")


def retime_dir(src: Path, out: Path, dt: float, stride: int) -> int:
    files = sorted(src.glob("*.jpg")) + sorted(src.glob("*.jpeg"))
    if stride > 1:
        files = files[::stride]
    out.mkdir(parents=True, exist_ok=True)
    for n, f in enumerate(files):
        shutil.copy(f, out / f"{n}.jpg")
    return len(files)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("out", help="output topomap directory")
    ap.add_argument("--robot", default="", help="r2d2 host to sample :12121 from")
    ap.add_argument("--port", type=int, default=12121)
    ap.add_argument("--dir", default="", help="instead of live: copy/retime a JPEG dir")
    ap.add_argument("--stride", type=int, default=1, help="keep every Nth file from --dir")
    ap.add_argument("--dt", type=float, default=1.0, help="seconds between nodes")
    args = ap.parse_args()

    out = Path(args.out)
    if args.dir:
        n = retime_dir(Path(args.dir), out, args.dt, args.stride)
        print(f"topomap: {n} nodes in {out}")
        return
    if not args.robot:
        raise SystemExit("give --robot <ip> (live stream) or --dir <path>")
    try:
        asyncio.run(record_stream(args.robot, args.port, out, args.dt))
    except KeyboardInterrupt:
        print(f"\ndone: {len(list(out.glob('*.jpg')))} nodes in {out}")


if __name__ == "__main__":
    main()
