from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import socket
import sys
from typing import Any, Dict, Optional, Union


SUPPORTED_COMMANDS = [
    "grantAccess",
    "getWifiList",
    "connectWifi",
    "face_detection",
    "mute",
    "power",
    "voice_recognition",
    "user_control",
    "change_name",
    "paired_list",
    "unpair",
    "move",
    "head-angle",
    "head-shift",
    "head-dir",
    "mode",
    "projector",
    "arm",
    "lightsaber",
    "led",
    "lcd",
    "debug",
    "ready",
    "reset-wdt",
    "gin",
    "play_sound",
    "self_update",
    "self_update_unsafe",
    "reset_mcu",
]


class RobotWebSocketClient:
    """Minimal WebSocket client for the robot command protocol."""

    def __init__(self, host: str, port: int, path: str = "/", timeout: float = 5.0) -> None:
        self.host = host
        self.port = port
        self.path = path
        self.timeout = timeout
        self._socket: Optional[socket.socket] = None

    def connect(self) -> None:
        if self._socket is not None:
            return

        sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        sock.settimeout(self.timeout)
        self._socket = sock

        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (
            f"GET {self.path} HTTP/1.1\r\n"
            f"Host: {self.host}:{self.port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        self._socket.sendall(request.encode("ascii"))

        response = b""
        while b"\r\n\r\n" not in response:
            chunk = self._socket.recv(4096)
            if not chunk:
                raise ConnectionError("websocket handshake failed")
            response += chunk

        if b"101" not in response:
            raise ConnectionError(f"unexpected handshake response: {response.decode('latin1', errors='ignore')}")

    def send_command(self, payload: Union[Dict[str, Any], str]) -> Dict[str, Any]:
        if isinstance(payload, str):
            data = payload.encode("utf-8")
        else:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")

        if self._socket is None:
            raise RuntimeError("not connected")

        self._socket.sendall(self._encode_frame(data))
        frame = self._recv_frame()
        return json.loads(frame.decode("utf-8"))

    def send_json(self, payload: Union[Dict[str, Any], str]) -> Dict[str, Any]:
        return self.send_command(payload)

    def close(self) -> None:
        if self._socket is not None:
            self._socket.close()
            self._socket = None

    @staticmethod
    def _encode_frame(payload: bytes, opcode: int = 0x1) -> bytes:
        header = bytearray()
        header.append(0x80 | opcode)
        length = len(payload)
        if length < 126:
            header.append(0x80 | length)
        elif length < 65536:
            header.append(0x80 | 126)
            header.extend(length.to_bytes(2, "big"))
        else:
            header.append(0x80 | 127)
            header.extend(length.to_bytes(8, "big"))

        mask_key = os.urandom(4)
        header.extend(mask_key)

        masked_payload = bytearray(payload)
        for i, byte in enumerate(masked_payload):
            masked_payload[i] = byte ^ mask_key[i % 4]
        return bytes(header) + bytes(masked_payload)

    def _recv_frame(self) -> bytes:
        if self._socket is None:
            raise RuntimeError("not connected")

        header = self._recv_exact(2)
        fin = bool(header[0] & 0x80)
        opcode = header[0] & 0x0F
        masked = bool(header[1] & 0x80)
        length = header[1] & 0x7F

        if length == 126:
            length = int.from_bytes(self._recv_exact(2), "big")
        elif length == 127:
            length = int.from_bytes(self._recv_exact(8), "big")

        mask_key = self._recv_exact(4) if masked else b""
        payload = self._recv_exact(length)
        if masked:
            payload = bytes(byte ^ mask_key[i % 4] for i, byte in enumerate(payload))

        if opcode == 0x8:
            raise ConnectionError("server closed the websocket")
        if not fin:
            return payload + self._recv_frame()
        return payload

    def _recv_exact(self, length: int) -> bytes:
        if self._socket is None:
            raise RuntimeError("not connected")

        chunks = bytearray()
        while len(chunks) < length:
            chunk = self._socket.recv(length - len(chunks))
            if not chunk:
                break
            chunks.extend(chunk)
        if len(chunks) != length:
            raise ConnectionError("unexpected EOF while reading websocket frame")
        return bytes(chunks)


    @staticmethod
    def build_parser() -> argparse.ArgumentParser:
        parser = argparse.ArgumentParser(
            description="Send JSON commands to the robot websocket server",
            formatter_class=argparse.RawTextHelpFormatter,
            epilog=(
                "Examples:\n"
                "  python3 robot_client.py move --power 1 --angle 0\n"
                "  python3 robot_client.py mode --mode 2\n"
                "  python3 robot_client.py head-angle --angle 45\n"
                "  python3 robot_client.py led --json '{\"r\":255,\"g\":0,\"b\":128}'\n"
                "  python3 robot_client.py lcd --json '{\"s\":1,\"l\":3}'\n"
                "  python3 robot_client.py grantAccess --uuid demo-uuid --new_name Robot\n"
                "  python3 robot_client.py connectWifi --ssid MyWiFi --password secret\n"
                "  python3 robot_client.py --cmd play_sound --sound_id 10"
            ),
        )
        parser.add_argument("--host", default="127.0.0.1", help="WebSocket host (default: 127.0.0.1)")
        parser.add_argument("--port", type=int, default=8887, help="WebSocket port (default: 8887)")
        parser.add_argument("--path", default="/", help="WebSocket path (default: /)")
        parser.add_argument("command", nargs="?", metavar="COMMAND", choices=SUPPORTED_COMMANDS, help="Robot command to send")
        parser.add_argument("--cmd", dest="legacy_cmd", help="Legacy option for specifying the command name")
        parser.add_argument("--json", help="Extra JSON fields as a single object, e.g. '{\"foo\":1}'")
        parser.add_argument("--enable", type=lambda value: value.lower() in {"1", "true", "yes", "on"}, help="Boolean flag for enable-style commands")
        parser.add_argument("--power", type=int, help="Motor or power-related value")
        parser.add_argument("--angle", type=int, help="Angle value for motion commands")
        parser.add_argument("--dir", type=int, help="Direction value")
        parser.add_argument("--mode", type=int, help="Mode value")
        parser.add_argument("--sound_id", type=int, help="Sound ID for play_sound")
        parser.add_argument("--uuid", help="UUID value for grantAccess or pairing commands")
        parser.add_argument("--ssid", help="Wi-Fi SSID")
        parser.add_argument("--password", help="Wi-Fi password")
        parser.add_argument("--new_name", help="New robot name")
        return parser

    @staticmethod
    def build_payload_from_args(args: argparse.Namespace) -> Dict[str, Any]:
        command = args.command or args.legacy_cmd
        if command is None:
            raise ValueError("a command is required")

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
    parser = RobotWebSocketClient.build_parser()
    args = parser.parse_args()

    if not args.command and not args.legacy_cmd:
        parser.error("a command is required (for example: move, grantAccess, getWifiList)")

    client = RobotWebSocketClient(args.host, args.port, path=args.path)
    try:
        client.connect()
        response = client.send_command(RobotWebSocketClient.build_payload_from_args(args))
        print(json.dumps(response, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:  # pragma: no cover - CLI safety
        print(str(exc), file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
