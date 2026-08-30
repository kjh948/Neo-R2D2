from __future__ import annotations

import argparse
import json
import os
import sys
import time
from typing import Any, Dict, Optional, Union

try:
    import serial  # type: ignore
    _HAS_PYSERIAL = True
except ImportError:
    _HAS_PYSERIAL = False

SUPPORTED_COMMANDS = [
    "ready",
    "debug",
    "gin",
    "reset-wdt",
    "shut-down",
    "move",
    "head-angle",
    "head-shift",
    "head-dir",
    "mode",
    "projector",
    "arm",
    "lightsaber",
    "d-head-power",
    "d-leg-power",
    "led",
    "lcd",
    "play_sound",
]


class UartCommandClient:
    """UART 직렬 포트로 JSON 명령을 전송하는 클라이언트."""

    def __init__(
        self,
        device: str = "/dev/ttyS2",
        baudrate: int = 115200,
        timeout: float = 1.0,
    ) -> None:
        self.device = device
        self.baudrate = baudrate
        self.timeout = timeout
        self._transport: Optional[Union[serial.Serial, os.FileIO]] = None
        self._fd: Optional[int] = None

    def connect(self) -> None:
        if self._transport is not None:
            return

        if _HAS_PYSERIAL:
            self._transport = serial.Serial(
                port=self.device,
                baudrate=self.baudrate,
                timeout=self.timeout,
                write_timeout=self.timeout,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE,
            )
        else:
            self._transport, self._fd = self._open_posix_serial(self.device, self.baudrate, self.timeout)

    def close(self) -> None:
        if self._transport is not None:
            try:
                self._transport.close()
            except Exception:
                pass
        self._transport = None
        self._fd = None

    def send_command(self, payload: Union[Dict[str, Any], str]) -> None:
        if self._transport is None:
            raise RuntimeError("UART port is not connected")

        data = self._to_line(payload)
        if hasattr(self._transport, "write"):
            self._transport.write(data)
            self._transport.flush()
        else:
            raise RuntimeError("Unsupported UART transport type")

    def read_response(self) -> Optional[Dict[str, Any]]:
        if self._transport is None:
            raise RuntimeError("UART port is not connected")

        if _HAS_PYSERIAL and hasattr(self._transport, "readline"):
            raw = self._transport.readline()
        elif self._fd is not None:
            raw = self._readline_from_fd(self._fd, self.timeout)
        else:
            raise RuntimeError("UART transport has no read path")

        if not raw:
            return None

        text = raw.decode("utf-8", errors="ignore").strip()
        if not text:
            return None

        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return None

    @staticmethod
    def _to_line(payload: Union[Dict[str, Any], str]) -> bytes:
        if isinstance(payload, dict):
            text = json.dumps(payload, ensure_ascii=False)
        elif isinstance(payload, str):
            text = payload
        else:
            raise TypeError("payload must be dict or str")
        return (text.strip() + "\n").encode("utf-8")

    @staticmethod
    def _open_posix_serial(device: str, baudrate: int, timeout: float) -> tuple[os.FileIO, int]:
        import fcntl
        import termios
        import tty

        fd = os.open(device, os.O_RDWR | os.O_NOCTTY | os.O_NONBLOCK)
        attrs = termios.tcgetattr(fd)

        attrs[0] = termios.IGNPAR
        attrs[1] = 0
        attrs[2] = termios.CS8 | termios.CREAD | termios.CLOCAL
        attrs[3] = 0
        attrs[4] = getattr(termios, f"B{baudrate}", termios.B115200)
        attrs[5] = getattr(termios, f"B{baudrate}", termios.B115200)

        attrs[6][termios.VMIN] = 0
        attrs[6][termios.VTIME] = int(timeout * 10)

        termios.tcsetattr(fd, termios.TCSANOW, attrs)
        try:
            os.set_blocking(fd, True)
        except AttributeError:
            flags = fcntl.fcntl(fd, fcntl.F_GETFL)
            fcntl.fcntl(fd, fcntl.F_SETFL, flags & ~os.O_NONBLOCK)

        transport = os.fdopen(fd, "r+b", buffering=0)
        return transport, fd

    @staticmethod
    def _readline_from_fd(fd: int, timeout: float) -> bytes:
        import select

        line = bytearray()
        end = time.time() + timeout
        while time.time() < end:
            ready, _, _ = select.select([fd], [], [], max(0, end - time.time()))
            if not ready:
                break
            chunk = os.read(fd, 1)
            if not chunk:
                break
            line.extend(chunk)
            if chunk == b"\n":
                break
        return bytes(line)

    @staticmethod
    def build_parser() -> argparse.ArgumentParser:
        parser = argparse.ArgumentParser(
            description="Send JSON commands to the robot over UART",
            formatter_class=argparse.RawTextHelpFormatter,
            epilog=(
                "Examples:\n"
                "  python3 uart_client.py move --power 1 --angle 0\n"
                "  python3 uart_client.py led --json '{\"r\":128,\"g\":0,\"b\":255}'\n"
                "  python3 uart_client.py grantAccess --uuid demo-uuid --device /dev/ttyS2\n"
            ),
        )
        parser.add_argument("--device", default="/dev/ttyS2", help="UART device path (default: /dev/ttyS2)")
        parser.add_argument("--baudrate", type=int, default=115200, help="Baud rate (default: 115200)")
        parser.add_argument("--timeout", type=float, default=1.0, help="Read timeout seconds (default: 1.0)")
        parser.add_argument("command", nargs="?", metavar="COMMAND", choices=SUPPORTED_COMMANDS, help="UART command to send")
        parser.add_argument("--cmd", dest="legacy_cmd", help="Legacy command name")
        parser.add_argument("--json", help="Additional JSON fields, e.g. '{\"r\":255,\"g\":0}'")
        parser.add_argument("--enable", type=lambda value: value.lower() in {"1", "true", "yes", "on"}, help="Boolean enable flag")
        parser.add_argument("--power", type=int, help="Power or intensity value")
        parser.add_argument("--angle", type=int, help="Angle value")
        parser.add_argument("--dir", type=int, help="Direction value")
        parser.add_argument("--mode", type=int, help="Mode value")
        parser.add_argument("--sound_id", type=int, help="Sound ID for play_sound")
        parser.add_argument("--uuid", help="UUID value for grantAccess")
        parser.add_argument("--ssid", help="Wi-Fi SSID")
        parser.add_argument("--password", help="Wi-Fi password")
        parser.add_argument("--new_name", help="Robot name")
        return parser

    @staticmethod
    def build_payload_from_args(args: argparse.Namespace) -> Dict[str, Any]:
        command = args.command or args.legacy_cmd
        if command is None:
            raise ValueError("command is required")

        payload: Dict[str, Any] = {"cmd": command}
        if args.json:
            payload.update(json.loads(args.json))
        for name in ("power", "angle", "dir", "mode", "sound_id", "uuid", "ssid", "password", "new_name"):
            if getattr(args, name) is not None:
                payload[name] = getattr(args, name)
        if args.enable is not None:
            payload["enable"] = args.enable
        return payload


def main() -> int:
    parser = UartCommandClient.build_parser()
    args = parser.parse_args()

    if not args.command and not args.legacy_cmd:
        parser.error("a command is required")

    client = UartCommandClient(device=args.device, baudrate=args.baudrate, timeout=args.timeout)
    try:
        client.connect()
        client.send_command(UartCommandClient.build_payload_from_args(args))
        response = client.read_response()
        if response is not None:
            print(json.dumps(response, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:  # pragma: no cover
        print(str(exc), file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
