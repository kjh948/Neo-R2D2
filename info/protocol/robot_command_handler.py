from __future__ import annotations

import json
from typing import Any, Callable, Dict, List, Optional


class RobotCommandProcessor:
    """Reference implementation of the command routing logic used by the app.

    The original Android app receives JSON commands from WebSocket/Bluetooth/UART,
    parses the `cmd` field, and routes them to handlers. This module mirrors that
    behavior in a simpler Python form.
    """

    COMMAND_HANDLERS = {
        "grantAccess": "handle_grant_access",
        "getWifiList": "handle_get_wifi_list",
        "connectWifi": "handle_connect_wifi",
        "face_detection": "handle_face_detection",
        "mute": "handle_mute",
        "power": "handle_power",
        "voice_recognition": "handle_voice_recognition",
        "user_control": "handle_user_control",
        "change_name": "handle_change_name",
        "paired_list": "handle_paired_list",
        "unpair": "handle_unpair",
        "move": "handle_move",
        "head-angle": "handle_head_angle",
        "head-shift": "handle_head_shift",
        "head-dir": "handle_head_dir",
        "mode": "handle_mode",
        "projector": "handle_projector",
        "arm": "handle_arm",
        "lightsaber": "handle_lightsaber",
        "led": "handle_led",
        "lcd": "handle_lcd",
        "debug": "handle_debug",
        "ready": "handle_ready",
        "reset-wdt": "handle_reset_wdt",
        "gin": "handle_gin",
        "play_sound": "handle_play_sound",
        "self_update": "handle_self_update",
        "self_update_unsafe": "handle_self_update_unsafe",
        "reset_mcu": "handle_reset_mcu",
    }

    def __init__(self, uart_sender: Optional[Callable[[Dict[str, Any]], None]] = None) -> None:
        self.state: Dict[str, Any] = {
            "clients": [],
            "robot_name": "r2d2",
            "face_detection_enabled": False,
            "voice_recognition_enabled": False,
            "mute_enabled": False,
            "controlling": False,
            "wifi_list": [],
            "current_ssid": None,
            "motion": {},
            "led": {},
            "lcd": {},
            "battery": None,
            "charging_status": None,
        }
        self.uart_sender = uart_sender or self._default_uart_sender

    def process_message(self, message: str) -> List[Dict[str, Any]]:
        results: List[Dict[str, Any]] = []
        for line in message.splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError as exc:
                results.append(self._build_error_response(None, 400, str(exc)))
                continue
            results.append(self.process_payload(payload))
        return results

    def process_payload(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        if not isinstance(payload, dict):
            return self._build_error_response(None, 400, "payload must be an object")

        cmd = payload.get("cmd")
        if not isinstance(cmd, str) or not cmd:
            return self._build_error_response(None, 400, "missing cmd")

        handler_name = self.COMMAND_HANDLERS.get(cmd)
        if handler_name is None:
            return self._build_error_response(cmd, 404, "unsupported command")

        handler = getattr(self, handler_name)
        return handler(payload)

    def _default_uart_sender(self, payload: Dict[str, Any]) -> None:
        print(json.dumps(payload, ensure_ascii=False))

    def _emit_uart(self, payload: Dict[str, Any]) -> None:
        self.uart_sender(payload)

    def _build_response(self, cmd: Optional[str], result_code: int = 0, **data: Any) -> Dict[str, Any]:
        response = {"resultCode": result_code}
        if cmd is not None:
            response["cmd"] = cmd
        response.update(data)
        return response

    def _build_error_response(self, cmd: Optional[str], result_code: int, message: str) -> Dict[str, Any]:
        return self._build_response(cmd, result_code=result_code, error=message)

    def handle_grant_access(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        uuid = payload.get("uuid")
        if not uuid:
            return self._build_error_response(payload.get("cmd"), 301, "uuid is required")
        self.state["clients"].append({"uuid": uuid, "deviceName": payload.get("deviceName", "unknown")})
        return self._build_response(payload.get("cmd"), robot={"uuid": uuid, "name": self.state["robot_name"]})

    def handle_get_wifi_list(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._build_response(payload.get("cmd"), wifi_list=self.state["wifi_list"], currentSSID=self.state["current_ssid"])

    def handle_connect_wifi(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        ssid = payload.get("ssid")
        password = payload.get("password")
        if not ssid:
            return self._build_error_response(payload.get("cmd"), 411, "ssid is required")
        self.state["current_ssid"] = ssid
        return self._build_response(payload.get("cmd"), connected=True)

    def handle_face_detection(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        enabled = bool(payload.get("enable", False))
        self.state["face_detection_enabled"] = enabled
        return self._build_response(payload.get("cmd"), enabled=enabled)

    def handle_mute(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        enabled = bool(payload.get("enable", False))
        self.state["mute_enabled"] = enabled
        return self._build_response(payload.get("cmd"), enabled=enabled)

    def handle_power(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart({**payload, "cmd": "shut-down"})
        return self._build_response(payload.get("cmd"), poweredOff=True)

    def handle_voice_recognition(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        enabled = bool(payload.get("enable", False))
        self.state["voice_recognition_enabled"] = enabled
        return self._build_response(payload.get("cmd"), enabled=enabled)

    def handle_user_control(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        enabled = bool(payload.get("enable", False))
        self.state["controlling"] = enabled
        return self._build_response(payload.get("cmd"), controlling=enabled)

    def handle_change_name(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        new_name = payload.get("newName")
        if not isinstance(new_name, str) or len(new_name) > 16:
            return self._build_error_response(payload.get("cmd"), 422, "invalid newName")
        self.state["robot_name"] = new_name
        return self._build_response(payload.get("cmd"), robotName=new_name)

    def handle_paired_list(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._build_response(payload.get("cmd"), clients=self.state["clients"])

    def handle_unpair(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        uuid = payload.get("uuid")
        if uuid is None:
            self.state["clients"] = []
        else:
            self.state["clients"] = [client for client in self.state["clients"] if client.get("uuid") != uuid]
        return self._build_response(payload.get("cmd"), clients=self.state["clients"])

    def handle_move(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["motion"] = {"power": payload.get("power", 0), "angle": payload.get("angle", 0)}
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), motion=self.state["motion"])

    def handle_head_angle(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["motion"]["angle"] = payload.get("angle", 0)
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), angle=payload.get("angle", 0))

    def handle_head_shift(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["motion"]["shift"] = payload.get("angle", 0)
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), angle=payload.get("angle", 0))

    def handle_head_dir(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["motion"]["dir"] = payload.get("dir", 0)
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), dir=payload.get("dir", 0))

    def handle_mode(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["motion"]["mode"] = payload.get("mode", 0)
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), mode=payload.get("mode", 0))

    def handle_projector(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), mode=payload.get("mode", 0))

    def handle_arm(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), power=payload.get("power", 0))

    def handle_lightsaber(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), power=payload.get("power", 0))

    def handle_led(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["led"] = {k: payload[k] for k in ("r", "g", "b", "y") if k in payload}
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), led=self.state["led"])

    def handle_lcd(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["lcd"] = {k: payload[k] for k in ("s", "l") if k in payload}
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), lcd=self.state["lcd"])

    def handle_debug(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), debug=True)

    def handle_ready(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), ready=True)

    def handle_reset_wdt(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), reset=True)

    def handle_gin(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.state["battery"] = payload.get("batt")
        self.state["charging_status"] = payload.get("charging-status")
        return self._build_response(payload.get("cmd"), battery=self.state["battery"], chargingStatus=self.state["charging_status"])

    def handle_play_sound(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), soundId=payload.get("sound_id"), interrupt=payload.get("interrupt", 0))

    def handle_self_update(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._build_response(payload.get("cmd"), url=payload.get("url"), updateStarted=True)

    def handle_self_update_unsafe(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        return self._build_response(payload.get("cmd"), url=payload.get("url"), updateStarted=True, unsafe=True)

    def handle_reset_mcu(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        self._emit_uart(payload)
        return self._build_response(payload.get("cmd"), resetMcu=True)
