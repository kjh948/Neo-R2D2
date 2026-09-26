"""navstack CLI: ``python -m navstack {serve|supervise|drive}``.

  serve      navserve WS server only (expects a llama-server already up)
  supervise  llama-server child process + navserve, one lifecycle (recommended)
  drive      navigator: camera -> navserve -> R2D2 (works from Mac or Pi5)
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import signal
import sys

from .config import LLAMA_HOST, LLAMA_PORT, NAVSERVE_PORT, NavConfig
from .server import NavServer, build_engine
from .llama_supervisor import LlamaServerProcess

logger = logging.getLogger("navstack")


def _add_common(p: argparse.ArgumentParser) -> None:
    p.add_argument("--task", choices=["vln", "tracking"], default="vln")
    p.add_argument("--preset", choices=["full", "lite", "micro", "nano"], default="full")
    p.add_argument("--model-dir", default=None)
    p.add_argument("--max-new-tokens", type=int, default=None)
    p.add_argument("--llama-url", default=f"http://{LLAMA_HOST}:{LLAMA_PORT}")
    p.add_argument("--log-level", default="INFO")
    # ---- vint backend (visualnav-transformer GNM/ViNT) ----
    p.add_argument("--backend", choices=["llama", "vint"], default="llama")
    p.add_argument("--vint-config", default="")
    p.add_argument("--vint-ckpt", default="")
    p.add_argument("--vint-threads", type=int, default=0)
    p.add_argument("--wp-scale-m", type=float, default=0.75)
    p.add_argument("--close-threshold-m", type=float, default=0.5)


def _cfg(args) -> NavConfig:
    from pathlib import Path
    cfg = NavConfig(
        task=args.task, preset=args.preset, llama_url=args.llama_url,
        max_new_tokens=args.max_new_tokens, log_level=args.log_level,
        backend=args.backend, vint_config=args.vint_config, vint_ckpt=args.vint_ckpt,
        vint_threads=args.vint_threads, wp_scale_m=args.wp_scale_m,
        close_threshold_m=args.close_threshold_m)
    if args.model_dir:
        cfg.model_dir = Path(args.model_dir)
    return cfg.load()


def cmd_serve(args) -> None:
    cfg = _cfg(args)
    cfg.host, cfg.port = args.host, args.port
    cfg.goal_image = args.goal_image
    cfg.topomap = args.topomap
    cfg.topomap_dir = args.topomap_dir
    from .server import serve
    serve(cfg)


def cmd_supervise(args) -> None:
    """llama-server (child) + navserve in one process tree (vosk_bridge pattern)."""
    cfg = _cfg(args)
    cfg.host, cfg.port = args.host, args.port
    if cfg.backend == "vint":
        cfg.goal_image = args.goal_image
        cfg.topomap = args.topomap
        cfg.topomap_dir = args.topomap_dir
        from .server import serve  # torch runs in-process; no llama child needed
        serve(cfg)
        return
    llama = LlamaServerProcess(
        host=LLAMA_HOST, port=LLAMA_PORT, binary=args.llama_binary,
        ngl=args.ngl, threads=args.threads, ctx=args.ctx)
    llama.start()

    def shutdown(*_):
        logger.info("shutting down")
        llama.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)
    engine = build_engine(cfg)
    server = NavServer(cfg, engine)
    try:
        asyncio.run(server.run())
    finally:
        llama.stop()


def cmd_drive(args) -> None:
    from .camera import make_source
    from .navigator import Navigator
    from .waypoints_to_cmd import MotionParams
    if not args.instruction and not args.goal_image and not args.topomap:
        ap_error = "drive needs --instruction (llama) or --goal-image/--topomap (vint)"
        raise SystemExit(ap_error)
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

    p = sub.add_parser("serve", help="navserve WebSocket server (external llama-server)")
    _add_common(p)
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=NAVSERVE_PORT)
    p.add_argument("--goal-image", default="", help="vint: fixed goal photo")
    p.add_argument("--topomap", default="", help="vint: ordered 0.jpg..N.jpg node dir")
    p.add_argument("--topomap-dir", default="", help="vint: base dir for named setGoal")
    p.set_defaults(fn=cmd_serve)

    p = sub.add_parser("supervise", help="llama-server + navserve in one lifecycle")
    _add_common(p)
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=NAVSERVE_PORT)
    p.add_argument("--llama-binary", default="llama-server")
    p.add_argument("--ngl", type=int, default=0, help="LLM layers to GPU (0 = CPU only)")
    p.add_argument("--threads", type=int, default=None)
    p.add_argument("--ctx", type=int, default=8192)
    p.add_argument("--goal-image", default="", help="vint: fixed goal photo")
    p.add_argument("--topomap", default="", help="vint: node dir for graph navigation")
    p.add_argument("--topomap-dir", default="", help="vint: base dir for named setGoal")
    p.set_defaults(fn=cmd_supervise)

    p = sub.add_parser("drive", help="navigator: camera -> navserve -> R2D2 motion")
    p.add_argument("--server", required=True, help="ws://navhost:8050")
    p.add_argument("--robot", required=True, help="R2D2 host (command port 8887)")
    p.add_argument("--instruction", default="", help="language goal (llama backend)")
    p.add_argument("--goal-image", default="", help="goal photo path (vint backend)")
    p.add_argument("--topomap", default="", help="node dir (vint graph navigation)")
    p.add_argument("--camera", default="r2d2", help="r2d2 | usb[:N] | dir:PATH")
    p.add_argument("--sample-fps", type=float, default=4.0)
    p.add_argument("--control-hz", type=float, default=3.0)
    p.add_argument("--stale-after", type=float, default=4.0)
    p.add_argument("--v-full", type=float, default=0.30, help="robot m/s at power 100")
    p.add_argument("--max-power", type=float, default=60.0)
    p.add_argument("--dry-run", action="store_true", help="no robot, log commands")
    p.add_argument("--show", action="store_true",
                   help="live OpenCV window: camera view + inference overlay (off by default; "
                        "needs opencv-python GUI build, not headless)")
    p.add_argument("--log-level", default="INFO")
    p.set_defaults(fn=cmd_drive)

    args = ap.parse_args(argv)
    args.fn(args)


if __name__ == "__main__":
    main()
