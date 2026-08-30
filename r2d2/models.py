from __future__ import annotations

import time
from typing import Any, Dict, Optional

# ``Robot.version`` is a hard-coded 15 in the Android build (it tracks the
# APK versionCode). Bump it together with the OTA manifest if you fork.
ROBOT_REPORTED_VERSION = 15


class Command:
    """Inbound MCU frame (``Model/Command.java``).

    Gson leaves absent ``int`` fields at whatever the no-arg constructor put
    there, which for ``r/b/y/g/s/l`` is ``-1`` (meaning "channel unchanged")
    and for the rest is ``0``.
    """

    __slots__ = (
        "cmd", "angle", "dir", "interrupt", "mode", "power", "value",
        "sound_id", "url", "r", "b", "y", "g", "s", "l",
    )

    def __init__(self, data: Optional[Dict[str, Any]] = None) -> None:
        data = data or {}
        self.cmd: Optional[str] = data.get("cmd")
        self.angle = _as_int(data.get("angle"), 0)
        self.dir = _as_int(data.get("dir"), 0)
        self.interrupt = _as_int(data.get("interrupt"), 0)
        self.mode = _as_int(data.get("mode"), 0)
        self.power = _as_int(data.get("power"), 0)
        self.value = _as_int(data.get("value"), 0)
        self.sound_id = _as_int(data.get("sound_id"), 0)
        self.url = data.get("url")
        self.r = _as_int(data.get("r"), -1)
        self.b = _as_int(data.get("b"), -1)
        self.y = _as_int(data.get("y"), -1)
        self.g = _as_int(data.get("g"), -1)
        self.s = _as_int(data.get("s"), -1)
        self.l = _as_int(data.get("l"), -1)

    def __repr__(self) -> str:
        return f"<Command {self.cmd} value={self.value} sound_id={self.sound_id}>"


class GinResponse:
    """The ``{"cmd":"gin", ...}`` status frame the MCU replies with."""

    __slots__ = (
        "cmd", "batt", "charging_status", "arm", "lightsaber", "projector",
        "mode", "head", "status", "lcd_s", "lcd_l", "error",
    )

    def __init__(self, data: Optional[Dict[str, Any]] = None) -> None:
        data = data or {}
        self.cmd = data.get("cmd")
        self.batt = _as_int(data.get("batt"), -1)
        self.charging_status = _as_int(data.get("charging-status"), 0)
        self.arm = _as_int(data.get("arm"), 0)
        self.lightsaber = _as_int(data.get("lightsaber"), 0)
        self.projector = _as_int(data.get("projector"), 0)
        self.mode = _as_int(data.get("mode"), 0)
        self.head = _as_int(data.get("head"), 0)
        self.status = _as_int(data.get("status"), 0)
        self.lcd_s = _as_int(data.get("lcd_s"), 0)
        self.lcd_l = _as_int(data.get("lcd_l"), 0)
        self.error = data.get("error")

    @property
    def arm_on(self) -> bool:
        return self.arm == 1

    @property
    def lightsaber_on(self) -> bool:
        return self.lightsaber == 1

    # The app exposes LCD state as a threshold, not a flag: anything below 2
    # means "closed".
    @property
    def short_lcd_open(self) -> bool:
        return self.lcd_s >= 2

    @property
    def long_lcd_open(self) -> bool:
        return self.lcd_l >= 2

    @property
    def error_text(self) -> str:
        return self.error or "NO ERROR"


class Client:
    """A paired controller app (``Model/EventJob/Client.java``)."""

    __slots__ = ("uuid", "device_name")

    def __init__(self, uuid: str, device_name: Optional[str] = None) -> None:
        self.uuid = uuid
        self.device_name = device_name

    def to_dict(self) -> Dict[str, Any]:
        return {"uuid": self.uuid, "device_name": self.device_name}

    def __eq__(self, other: Any) -> bool:
        return isinstance(other, Client) and other.uuid == self.uuid

    def __hash__(self) -> int:
        return hash(self.uuid)

    def __repr__(self) -> str:
        return f"<Client {self.uuid} {self.device_name}>"


class Robot:
    """The status blob pushed to clients as ``{"cmd":"gin","robot":{...}}``."""

    def __init__(
        self,
        state,
        mode: int = 1,
        ip: str = "",
        ap_mode: bool = False,
        ssid: Optional[str] = None,
    ) -> None:
        self.state = state
        self.mode = mode
        self.ip = ip
        self.ap_mode = ap_mode
        self.ssid = ssid

    def to_dict(self) -> Dict[str, Any]:
        s = self.state
        return {
            "name": s.name,
            "uuid": s.udid,
            "face_detection": s.face_detection,
            "voice_recognition": s.voice_recognition,
            "mute": s.mute,
            "ip": self.ip,
            "battery": s.battery,
            "charging": s.charging,
            "lightsaber": s.lightsaber,
            "arm": s.arm,
            "projector": s.projector,
            "timestamp": int(time.time() * 1000),
            "mode": self.mode,
            "lcd_s": s.short_lcd,
            "lcd_l": s.long_lcd,
            "ap_mode": self.ap_mode,
            "version": ROBOT_REPORTED_VERSION,
            "ssid": self.ssid if self.ssid is not None else s.ssid,
            "self_update": s.self_update_state,
            "update_dl_progress": s.update_dl_progress,
            "error": s.error,
        }


def _as_int(value: Any, default: int) -> int:
    if value is None:
        return default
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return default
