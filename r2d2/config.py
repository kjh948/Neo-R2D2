from __future__ import annotations

import json
import os
from dataclasses import dataclass, field, asdict, fields
from typing import Any, Dict

DEFAULT_CONFIG_PATHS = (
    "r2d2.json",
    os.path.expanduser("~/.config/r2d2/config.json"),
    "/etc/r2d2/config.json",
)

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _env(name: str, default):
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    if isinstance(default, bool):
        return raw.strip().lower() in {"1", "true", "yes", "on"}
    if isinstance(default, int) and not isinstance(default, bool):
        try:
            return int(raw, 0)
        except ValueError:
            return default
    if isinstance(default, float):
        try:
            return float(raw)
        except ValueError:
            return default
    return raw


@dataclass
class Config:
    """Runtime knobs for the robot host process.

    Every value can be overridden by a JSON file (``--config``) and then by an
    environment variable (``R2D2_*``), in that order of increasing precedence.
    """

    # --- UART link to the MCU -------------------------------------------------
    # The Android build hard-codes /dev/ttyS2 in SerialPort.java; NanoPi/Pi
    # images surface the MCU on either name depending on the overlay used.
    serial_port: str = _env("R2D2_SERIAL_PORT", "/dev/ttyS2")
    baudrate: int = _env("R2D2_BAUDRATE", 115200)
    serial_read_timeout: float = _env("R2D2_SERIAL_READ_TIMEOUT", 1.0)
    mock_serial: bool = _env("R2D2_MOCK_SERIAL", False)

    # --- WebSocket command server --------------------------------------------
    ws_host: str = _env("R2D2_WS_HOST", "0.0.0.0")
    ws_port: int = _env("R2D2_WS_PORT", 8887)
    ws_path: str = _env("R2D2_WS_PATH", "/")

    # --- Video streaming server ----------------------------------------------
    # Verified: StreamingServer extends WebSocketServer on WEB_SOCKET_PORT=12121.
    stream_host: str = _env("R2D2_STREAM_HOST", "0.0.0.0")
    stream_port: int = _env("R2D2_STREAM_PORT", 12121)
    camera_index: int = _env("R2D2_CAMERA_INDEX", 0)
    camera_width: int = _env("R2D2_CAMERA_WIDTH", 640)
    camera_height: int = _env("R2D2_CAMERA_HEIGHT", 480)
    camera_jpeg_quality: int = _env("R2D2_CAMERA_JPEG_QUALITY", 70)
    mock_camera: bool = _env("R2D2_MOCK_CAMERA", False)

    # --- UDP discovery --------------------------------------------------------
    discovery_enabled: bool = _env("R2D2_DISCOVERY", True)
    discovery_port: int = _env("R2D2_DISCOVERY_PORT", 8889)

    # --- Behaviour switches ---------------------------------------------------
    face_detection_enabled: bool = _env("R2D2_FACE_DETECTION", True)
    voice_recognition_enabled: bool = _env("R2D2_VOICE_RECOGNITION", False)
    mute: bool = _env("R2D2_MUTE", False)

    # Power-off is an MCU command first; the host shutdown that follows it in
    # the Android app is opt-in so a stray client command cannot halt the board.
    allow_host_shutdown: bool = _env("R2D2_ALLOW_HOST_SHUTDOWN", False)

    # --- Assets / state -------------------------------------------------------
    sound_dir: str = _env("R2D2_SOUND_DIR", os.path.join(REPO_ROOT, "sound_effects"))
    cascade_dir: str = _env("R2D2_CASCADE_DIR", os.path.join(REPO_ROOT, "sound_effects"))
    state_file: str = _env("R2D2_STATE_FILE", os.path.expanduser("~/.config/r2d2/state.json"))

    log_level: str = _env("R2D2_LOG_LEVEL", "info")

    extra: Dict[str, Any] = field(default_factory=dict)

    @classmethod
    def load(cls, path: str | None = None) -> "Config":
        cfg = cls()
        candidates = [path] if path else list(DEFAULT_CONFIG_PATHS)
        for candidate in candidates:
            if not candidate or not os.path.isfile(candidate):
                continue
            try:
                with open(candidate, "r", encoding="utf-8") as handle:
                    data = json.load(handle)
            except (OSError, ValueError):
                continue
            if not isinstance(data, dict):
                continue
            known = {f.name for f in fields(cls)} - {"extra"}
            for key, value in data.items():
                if key in known:
                    setattr(cfg, key, value)
                else:
                    cfg.extra[key] = value
            break
        return cfg

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data.pop("extra", None)
        data.update(self.extra)
        return data
