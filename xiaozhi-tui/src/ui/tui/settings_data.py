"""TUI 设置：配置字段定义与读写（接 ConfigManager + CONFIG_CHANGED）."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from src.logging import get_logger
from src.utils.config_manager import get_config

logger = get_logger()


@dataclass(frozen=True)
class SettingField:
    """一个可编辑配置项."""

    path: str
    label: str
    kind: str = "str"  # str | int | bool | choice
    choices: tuple[str, ...] = ()
    help: str = ""


# 第一期：系统 / 音频 / 摄像头 / 唤醒词
SETTING_SECTIONS: list[tuple[str, list[SettingField]]] = [
    (
        "System",
        [
            SettingField(
                "SYSTEM_OPTIONS.NETWORK.OTA_VERSION_URL",
                "OTA URL",
                help="OTA / activation config endpoint",
            ),
            SettingField(
                "SYSTEM_OPTIONS.NETWORK.WEBSOCKET_URL",
                "WebSocket URL",
                help="WebSocket server address",
            ),
            SettingField(
                "SYSTEM_OPTIONS.DEVICE_ID",
                "Device ID",
                help="Device-Id (usually the MAC)",
            ),
            SettingField(
                "SYSTEM_OPTIONS.CLIENT_ID",
                "Client ID",
                help="Client-Id",
            ),
        ],
    ),
    (
        "Audio",
        [
            SettingField(
                "AUDIO_DEVICES.input_device_name",
                "Input device",
                help="Match mic by name (IDs change on hot-plug)",
            ),
            SettingField(
                "AUDIO_DEVICES.output_device_name",
                "Output device",
                help="Match speaker / headphones by name",
            ),
            SettingField(
                "AUDIO_DEVICES.opus_output_sample_rate",
                "Opus output rate",
                kind="choice",
                choices=("24000", "16000"),
                help="Official 24000 / third-party often 16000",
            ),
            SettingField(
                "AUDIO_DEVICES.frame_duration",
                "Frame ms",
                kind="choice",
                choices=("20", "40", "60"),
                help="20 low latency / 60 low CPU",
            ),
        ],
    ),
    (
        "Camera",
        [
            SettingField(
                "CAMERA.backend",
                "Capture backend",
                kind="choice",
                choices=("auto", "opencv", "picamera2"),
                help="auto tries OpenCV, then Pi CSI",
            ),
            SettingField(
                "CAMERA.device",
                "Device path",
                help="e.g. /dev/video0; takes precedence over index",
            ),
            SettingField(
                "CAMERA.camera_index",
                "Device index",
                kind="int",
                help="Numeric OpenCV index",
            ),
            SettingField(
                "CAMERA.frame_width",
                "Width",
                kind="int",
            ),
            SettingField(
                "CAMERA.frame_height",
                "Height",
                kind="int",
            ),
        ],
    ),
    (
        "Wake word",
        [
            SettingField(
                "WAKE_WORD_OPTIONS.USE_WAKE_WORD",
                "Enable wake word",
                kind="bool",
            ),
            SettingField(
                "WAKE_WORD_OPTIONS.WAKE_WORD",
                "Wake word",
                help='e.g. "hello xiaozhi"',
            ),
            SettingField(
                "WAKE_WORD_OPTIONS.WAKE_WORD_LANG",
                "Language",
                kind="choice",
                choices=("zh", "en"),
            ),
        ],
    ),
]


def load_setting_values() -> dict[str, str]:
    """读取当前配置为字符串表（path -> 显示值）."""
    cfg = get_config()
    values: dict[str, str] = {}
    for _section, fields in SETTING_SECTIONS:
        for f in fields:
            raw = cfg.get_config(f.path, "")
            if f.kind == "bool":
                values[f.path] = "true" if bool(raw) else "false"
            elif raw is None:
                values[f.path] = ""
            else:
                values[f.path] = str(raw)
    return values


def parse_field_value(field: SettingField, text: str) -> Any:
    """把输入框字符串转成配置值."""
    s = (text or "").strip()
    if field.kind == "int":
        if s == "":
            return 0
        return int(s)
    if field.kind == "bool":
        return s.lower() in ("1", "true", "yes", "on", "是")
    if field.kind == "choice":
        if field.choices and s not in field.choices:
            # 仍写入用户值，由上层校验提示
            return s
        if field.path.endswith("opus_output_sample_rate") or field.path.endswith(
            "frame_duration"
        ):
            try:
                return int(s)
            except ValueError:
                return s
        return s
    return s


def save_settings(values: dict[str, str]) -> tuple[bool, str]:
    """批量写配置并落盘.

    Returns:
        (ok, message)
    """
    cfg = get_config()
    updates: dict[str, Any] = {}
    try:
        for _section, fields in SETTING_SECTIONS:
            for f in fields:
                if f.path not in values:
                    continue
                updates[f.path] = parse_field_value(f, values[f.path])
        if not updates:
            return True, "No changes"
        ok = cfg.update_configs(updates)
        if not ok:
            return False, "Save failed (could not write config)"
        logger.info(f"TUI saved {len(updates)} setting(s)")
        return True, f"Saved {len(updates)} setting(s)"
    except Exception as e:
        logger.error(f"TUI failed to save settings: {e}", exc_info=True)
        return False, f"Save failed: {e}"
