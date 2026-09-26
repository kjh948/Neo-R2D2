"""navstack CLI: ``python -m navstack {serve|drive}``.

  serve   navserve WebSocket server (in-process torch; model from --vint-*)
  drive   navigator: camera -> navserve -> R2D2 motion

Examples
  python -m navstack serve                         # NoMaD default (goal-less)
  python -m navstack serve --vint-config .../vint.yaml --vint-ckpt .../vint.pth
  python -m navstack drive --server ws://h:8050 --robot <pi> --camera r2d2 \
      [--goal-image dest.jpg | --topomap dir] [--show] [--dry-run]
"""

from __future__ import annotations

import argparse
import asyncio
import logging

from .config import DEFAULT_PORT, NavConfig

logger = logging.getLogger("navstack")


def _add_model(p: argparse.ArgumentParser) -> None:
    p.add_argument("--vint-config", default=NavConfig.vint_config,
                   help="upstream training yaml (nomad/vint/gnm)")
    p.add_argument("--vint-ckpt", default=NavConfig.vint_ckpt, help="*.pth weights")
    p.add_argument("--vint-threads", type=int, default=0)
    p.add_argument("--wp-scale-m", type=float, default=0.75)
    p.add_argument("--close-threshold-m", type=float, default=0.5)
    p.add_argument("--subgoal-radius", type=int, default=3)
    p.add_argument("--y-sign", type=float, default=1.0,
                   help="-1 if the robot consistently steers the wrong way")


def cmd_serve(args) -> None:
    from .server import serve
    cfg = NavConfig(
        host=args.host, port=args.port, log_level=args.log_level,
        vint_config=args.vint_config, vint_ckpt=args.vint_ckpt,
        vint_threads=args.vint_threads, goal_image=args.goal_image,
        topomap=args.topomap, topomap_dir=args.topomap_dir,
        wp_scale_m=args.wp_scale_m, close_threshold_m=args.close_threshold_m,
        subgoal_radius=args.subgoal_radius, y_sign=args.y_sign)
    serve(cfg)


def cmd_drive(args) -> None:
    from .camera import make_source
    from .navigator import Navigator
    from .waypoints_to_cmd import MotionParams
    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO))
    motion = MotionParams(v_full_mps=args.v_full, max_power=args.max_power)
    source = make_source(args.camera, robot_host=args.robot)
    nav = Navigator(
        server_url=args.server, robot_host=args.robot, instruction=args.instruction,
        frame_source=source, sample_fps=args.sample_fps, control_hz=args.control_hz,
        stale_after_s=args.stale_after, motion=motion, dry_run=args.dry_run,
        goal_image=args.goal_image, goal_topomap=args.topomap, show=args.show)
    try:
        asyncio.run(nav.run())
    except KeyboardInterrupt:
        pass


def main(argv=None) -> None:
    ap = argparse.ArgumentParser(prog="navstack")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("serve", help="navserve WebSocket server")
    _add_model(p)
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=DEFAULT_PORT)
    p.add_argument("--goal-image", default="", help="optional fixed goal photo")
    p.add_argument("--topomap", default="", help="optional ordered 0.jpg..N.jpg dir")
    p.add_argument("--topomap-dir", default="", help="base dir for named setGoal")
    p.add_argument("--log-level", default="INFO")
    p.set_defaults(fn=cmd_serve)

    p = sub.add_parser("drive", help="camera -> navserve -> R2D2")
    p.add_argument("--server", required=True, help="ws://navhost:8050")
    p.add_argument("--robot", required=True, help="R2D2 host (command port 8887)")
    p.add_argument("--instruction", default="",
                   help="ignored by the vint backend (protocol compat)")
    p.add_argument("--goal-image", default="", help="goal photo (omit with NoMaD)")
    p.add_argument("--topomap", default="", help="node dir for graph navigation")
    p.add_argument("--camera", default="r2d2", help="r2d2 | usb[:N] | dir:PATH")
    p.add_argument("--sample-fps", type=float, default=4.0)
    p.add_argument("--control-hz", type=float, default=3.0)
    p.add_argument("--stale-after", type=float, default=4.0)
    p.add_argument("--v-full", type=float, default=0.30, help="robot m/s at power 100")
    p.add_argument("--max-power", type=float, default=60.0)
    p.add_argument("--dry-run", action="store_true", help="no robot, log commands")
    p.add_argument("--show", action="store_true",
                   help="OpenCV window: camera + inference overlay (GUI opencv build)")
    p.add_argument("--log-level", default="INFO")
    p.set_defaults(fn=cmd_drive)

    args = ap.parse_args(argv)
    args.fn(args)


if __name__ == "__main__":
    main()
