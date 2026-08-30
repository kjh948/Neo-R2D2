from __future__ import annotations

import argparse
import logging
import signal
import sys
import threading

from .app import RobotApplication
from .config import Config
from .log import configure_logging, get_logger, level_from_name

LOG = get_logger("main")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python3 -m r2d2",
        description="R2D2 host controller — Python port of com.bullb.r2d2_nanopisystem",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  python3 -m r2d2 --mock                      # run with no hardware\n"
            "  python3 -m r2d2 --port /dev/ttyS2           # the Android build's default\n"
            "  python3 -m r2d2 --config /etc/r2d2/config.json\n"
            "\n"
            "Commands are documented in r2d2/README.md; the wire protocol matches\n"
            "info/protocol/UART_COMMANDS.md and the websocket console protocol.\n"
        ),
    )
    parser.add_argument("--config", help="path to a JSON config file")
    parser.add_argument("--port", "--serial-port", dest="port", help="MCU serial device (default: /dev/ttyS2)")
    parser.add_argument("--baudrate", type=int, help="serial baud rate (default: 115200)")
    parser.add_argument("--ws-port", type=int, help="websocket command port (default: 8887)")
    parser.add_argument("--stream-port", type=int, help="video websocket port (default: 12121)")
    parser.add_argument("--camera", help="cv2 camera index or device path")
    parser.add_argument("--sound-dir", help="directory holding the .mp3 sound effects")
    parser.add_argument("--mock", action="store_true", help="run without serial, camera or audio hardware")
    parser.add_argument("--no-face-detection", action="store_true", help="disable the camera/face pipeline")
    parser.add_argument("--voice", action="store_true", help="enable voice recognition at boot")
    parser.add_argument("--no-discovery", action="store_true", help="disable the UDP announce broadcast")
    parser.add_argument("--allow-host-shutdown", action="store_true",
                        help="let a power-off command halt this host too (the app does)")
    parser.add_argument("--log-level", default="info",
                        help="trace|debug|info|warning|error (default: info)")
    parser.add_argument("--status", action="store_true", help="print status every 30s to the log")
    return parser


def apply_args(config: Config, args: argparse.Namespace) -> Config:
    if args.port:
        config.serial_port = args.port
    if args.baudrate:
        config.baudrate = args.baudrate
    if args.ws_port:
        config.ws_port = args.ws_port
    if args.stream_port:
        config.stream_port = args.stream_port
    if args.sound_dir:
        config.sound_dir = args.sound_dir
    if args.camera is not None:
        config.camera_index = int(args.camera) if str(args.camera).isdigit() else 0
        if not str(args.camera).isdigit():
            LOG.warning("--camera paths other than an index are not supported by cv2 on this build; "
                        "using index %s", config.camera_index)
    if args.no_face_detection:
        config.face_detection_enabled = False
    if args.voice:
        config.voice_recognition_enabled = True
    if args.no_discovery:
        config.discovery_enabled = False
    if args.allow_host_shutdown:
        config.allow_host_shutdown = True
    return config


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    configure_logging(level_from_name(args.log_level))
    if args.mock:
        LOG.info("mock mode: no serial, camera or audio output")

    config = apply_args(Config.load(args.config), args)
    app = RobotApplication(config, mock=args.mock)

    def handle_signal(signum, _frame) -> None:
        LOG.info("received signal %s, shutting down", signum)
        app.request_stop()

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            signal.signal(sig, handle_signal)
        except (ValueError, OSError):  # pragma: no cover - non-main thread
            pass

    try:
        app.start()
    except Exception:
        LOG.exception("failed to start")
        app.stop()
        return 1

    if args.status:
        reporter = threading.Thread(target=_status_loop, args=(app,), daemon=True)
        reporter.start()

    # Teardown happens on the main thread, never inside the signal handler: it
    # joins reader/pump threads that a handler could deadlock against.
    while not app.stopping:
        app._stop.wait(0.5)
    app.stop()
    return 0


def _status_loop(app) -> None:
    while not app._stop.is_set():
        LOG.info("status: %s", app.status())
        app._stop.wait(30.0)


if __name__ == "__main__":
    sys.exit(main())
