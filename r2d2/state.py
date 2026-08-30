from __future__ import annotations

import json
import os
import threading
from typing import Any, Dict, List, Optional

from .log import get_logger

LOG = get_logger("state")

NO_ERROR = "NO ERROR"

CHARGING_NOT_CHARGED = 0
CHARGING_CHARGING = 1
CHARGING_CHARGING_FINISHED = 2

UPDATE_NOT_UPDATING = 0
UPDATE_DOWNLOADING = 1
UPDATE_INSTALLING = 2

LOW_BATTERY_PERCENTAGE = 20
UNAUTHORIZED = 401

_DEFAULTS: Dict[str, Any] = {
    "robot_name": None,
    "robot_udid": None,
    "robot_access_key": None,
    "clientList": [],
    "face_detection": True,
    "voiceRecognition": True,
    "mute": False,
    "battery": -1,
    "self_update_state": UPDATE_NOT_UPDATING,
    "update_dl_progress": 0,
    "lightsaber": False,
    "arm": False,
    "projector": 0,
    "restart_version": 0,
    "lcd_s": False,
    "lcd_l": False,
    "robot_charging": CHARGING_NOT_CHARGED,
    "robot_error": NO_ERROR,
    "ap_mode": False,
    "ssid": None,
    "user_control": False,
}


class RobotState:
    """Port of ``utils/RobotPreference`` — the ``robot`` SharedPreferences file.

    Holds the robot's persisted identity and the MCU-reported status snapshot.
    Writes are atomic (temp file + rename) and guarded by a lock because the
    UART reader, the behaviour timers and the websocket handlers all mutate it.
    """

    def __init__(self, path: Optional[str] = None) -> None:
        self._path = path
        self._lock = threading.RLock()
        self._data: Dict[str, Any] = dict(_DEFAULTS)
        if path and os.path.isfile(path):
            self._load()

    def _load(self) -> None:
        try:
            with open(self._path, "r", encoding="utf-8") as handle:
                stored = json.load(handle)
        except (OSError, ValueError) as exc:
            LOG.warning("cannot read state file %s: %s", self._path, exc)
            return
        if isinstance(stored, dict):
            for key, value in stored.items():
                self._data[key] = value
        # A process crash mid-update must never leave the robot muted or the
        # LEDs latched off, so the volatile runtime flags reset on boot.
        self._data["self_update_state"] = UPDATE_NOT_UPDATING
        self._data["update_dl_progress"] = 0

    def _save(self) -> None:
        if not self._path:
            return
        directory = os.path.dirname(self._path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        tmp = f"{self._path}.tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as handle:
                json.dump(self._data, handle, indent=2, ensure_ascii=False)
            os.replace(tmp, self._path)
        except OSError as exc:  # pragma: no cover - disk full / read-only fs
            LOG.error("cannot persist state to %s: %s", self._path, exc)

    # -- generic access -------------------------------------------------------
    def get(self, key: str, default: Any = None) -> Any:
        with self._lock:
            return self._data.get(key, _DEFAULTS.get(key, default))

    def set(self, key: str, value: Any) -> None:
        with self._lock:
            if self._data.get(key) == value:
                return
            self._data[key] = value
            self._save()

    def snapshot(self) -> Dict[str, Any]:
        with self._lock:
            return dict(self._data)

    # -- typed accessors mirroring RobotPreference ---------------------------
    @property
    def name(self) -> Optional[str]:
        return self.get("robot_name")

    @name.setter
    def name(self, value: str) -> None:
        self.set("robot_name", value)

    @property
    def udid(self) -> Optional[str]:
        return self.get("robot_udid")

    @udid.setter
    def udid(self, value: str) -> None:
        self.set("robot_udid", value)

    @property
    def access_key(self) -> Optional[str]:
        return self.get("robot_access_key")

    @access_key.setter
    def access_key(self, value: Optional[str]) -> None:
        self.set("robot_access_key", value)

    @property
    def battery(self) -> int:
        return int(self.get("battery"))

    @battery.setter
    def battery(self, value: int) -> None:
        self.set("battery", int(value))

    @property
    def charging(self) -> int:
        return int(self.get("robot_charging"))

    @charging.setter
    def charging(self, value: int) -> None:
        self.set("robot_charging", int(value))

    @property
    def low_battery(self) -> bool:
        # ``getFrontBaseMode`` gates on ``battery < 20``; the saved default of
        # -1 (never reported by the MCU yet) therefore counts as low.
        return self.battery < LOW_BATTERY_PERCENTAGE

    @property
    def arm(self) -> bool:
        return bool(self.get("arm"))

    @arm.setter
    def arm(self, value: bool) -> None:
        self.set("arm", bool(value))

    @property
    def lightsaber(self) -> bool:
        return bool(self.get("lightsaber"))

    @lightsaber.setter
    def lightsaber(self, value: bool) -> None:
        self.set("lightsaber", bool(value))

    @property
    def projector(self) -> int:
        return int(self.get("projector"))

    @projector.setter
    def projector(self, value: int) -> None:
        self.set("projector", int(value))

    @property
    def short_lcd(self) -> bool:
        return bool(self.get("lcd_s"))

    @short_lcd.setter
    def short_lcd(self, value: bool) -> None:
        self.set("lcd_s", bool(value))

    @property
    def long_lcd(self) -> bool:
        return bool(self.get("lcd_l"))

    @long_lcd.setter
    def long_lcd(self, value: bool) -> None:
        self.set("lcd_l", bool(value))

    @property
    def error(self) -> str:
        return self.get("robot_error") or NO_ERROR

    @error.setter
    def error(self, value: str) -> None:
        self.set("robot_error", value or NO_ERROR)

    @property
    def face_detection(self) -> bool:
        return bool(self.get("face_detection"))

    @face_detection.setter
    def face_detection(self, value: bool) -> None:
        self.set("face_detection", bool(value))

    @property
    def voice_recognition(self) -> bool:
        return bool(self.get("voiceRecognition"))

    @voice_recognition.setter
    def voice_recognition(self, value: bool) -> None:
        self.set("voiceRecognition", bool(value))

    @property
    def mute(self) -> bool:
        return bool(self.get("mute"))

    @mute.setter
    def mute(self, value: bool) -> None:
        self.set("mute", bool(value))

    @property
    def ap_mode(self) -> bool:
        return bool(self.get("ap_mode"))

    @ap_mode.setter
    def ap_mode(self, value: bool) -> None:
        self.set("ap_mode", bool(value))

    @property
    def ssid(self) -> Optional[str]:
        return self.get("ssid")

    @ssid.setter
    def ssid(self, value: Optional[str]) -> None:
        self.set("ssid", value)

    @property
    def user_control(self) -> bool:
        return bool(self.get("user_control"))

    @user_control.setter
    def user_control(self, value: bool) -> None:
        self.set("user_control", bool(value))

    @property
    def self_update_state(self) -> int:
        return int(self.get("self_update_state"))

    @self_update_state.setter
    def self_update_state(self, value: int) -> None:
        self.set("self_update_state", int(value))

    @property
    def update_dl_progress(self) -> int:
        return int(self.get("update_dl_progress"))

    @update_dl_progress.setter
    def update_dl_progress(self, value: int) -> None:
        self.set("update_dl_progress", int(value))

    @property
    def restart_version(self) -> int:
        return int(self.get("restart_version"))

    def bump_restart_version(self) -> int:
        with self._lock:
            self._data["restart_version"] = self.restart_version + 1
            self._save()
            return self._data["restart_version"]

    # -- paired client list ---------------------------------------------------
    @property
    def clients(self) -> List[Dict[str, Any]]:
        return list(self.get("clientList") or [])

    def add_client(self, uuid: str, device_name: str) -> List[Dict[str, Any]]:
        with self._lock:
            clients = list(self._data.get("clientList") or [])
            if not any(client.get("uuid") == uuid for client in clients):
                clients.append({"uuid": uuid, "device_name": device_name})
                self._data["clientList"] = clients
                self._save()
            return list(clients)

    def remove_client(self, uuid: str) -> List[Dict[str, Any]]:
        with self._lock:
            clients = [c for c in (self._data.get("clientList") or []) if c.get("uuid") != uuid]
            self._data["clientList"] = clients
            self._save()
            return list(clients)

    def clear_clients(self) -> None:
        self.set("clientList", [])

    def is_paired(self, uuid: Optional[str]) -> bool:
        if not uuid:
            return False
        return any(client.get("uuid") == uuid for client in self.clients)

    def clear_preference(self) -> None:
        """``RobotPreference.clearPreference`` — keeps name/udid/version/mute."""
        with self._lock:
            for key, value in _DEFAULTS.items():
                if key in ("robot_name", "robot_udid", "restart_version", "mute"):
                    continue
                self._data[key] = list(value) if isinstance(value, list) else value
            # The original resets the projector to -1 here, which the app treats
            # as "unknown" until the next gin reply.
            self._data["projector"] = -1
            self._save()
