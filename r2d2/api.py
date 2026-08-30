from __future__ import annotations

import json
import threading
import time
from typing import Any, Callable, Dict, List, Optional

from .log import get_logger
from .models import Command

LOG = get_logger("api")

# ---------------------------------------------------------------------------
# Command groups, exactly as ``RobotApiHandler`` partitions them.
# ---------------------------------------------------------------------------
AUTH_COMMANDS = frozenset({"grantAccess"})

NORMAL_COMMANDS = frozenset({
    "getWifiList",
    "connectWifi",
    "face_detection",
    "voice_recognition",
    "mute",
    "power",
    "user_control",
    "change_name",
    "paired_list",
    "unpair",
})

# ``CommandReceiver``'s switch: raw motion/hardware pass-through. Only reached
# when the connection is validated and the robot is not in pair mode.
MOTION_COMMANDS = frozenset({
    "move",
    "move-head",
    "head-dir",
    "projector",
    "reset-wdt",
    "d-head-power",
    "d-leg-power",
    "lcd",
    "led",
    "mode",
    "play_sound",
    "self_update",
    "self_update_unsafe",
    "reset_mcu",
})

ALL_COMMANDS = AUTH_COMMANDS | NORMAL_COMMANDS | MOTION_COMMANDS

# Error codes observed in ``RobotApi/Error.java``...
OK = 0
ERROR_GENERIC = 1
ERROR_NO_UUID = 301
ERROR_UNAUTHORIZED = 401
ERROR_WIFI_UNAUTHORIZED = 411
ERROR_INVALID_NAME = 422
ERROR_CLIENT_NOT_FOUND = 423
ERROR_VIDEO_IN_USE = 421
ERROR_STREAMING_IN_PAIR = 501  # declared in Error.java, never emitted by the app

# ...plus ``WifiService``, whose positive return values are echoed verbatim as
# ``resultCode`` by ``RobotApiHandler.connectWifi``.
ERROR_WIFI_UNSUPPORTED = 410
ERROR_WIFI_INVALID_CONFIG = 412
ERROR_WIFI_NOT_FOUND = 414

MAX_NAME_LENGTH = 16
MIN_SELF_UPDATE_BATTERY = 50

ESTABLISH_TIMEOUT = 10.0
CONTROL_TIMEOUT = 12.0


class ClientSession:
    """Port of ``SocketConnection``: one validated controller connection."""

    def __init__(
        self,
        session_id: str,
        send: Callable[[str], bool],
        close: Callable[[], None],
        on_control_change: Optional[Callable[[], None]] = None,
    ) -> None:
        self.session_id = session_id
        self._send = send
        self._close = close
        self.on_control_change = on_control_change
        self.uuid: Optional[str] = None
        self.device_name: str = "unknown"
        self.valid = False
        self.controlling = False
        self.buffer = ""
        # Set when a response must reach the client before the socket drops,
        # which is how ``grantAccess`` reports 301/401.
        self.close_after_send = False
        self._control_timer: Optional[threading.Timer] = None
        self._establish_timer: Optional[threading.Timer] = None

    def start_establish_timer(self, timeout: float = ESTABLISH_TIMEOUT) -> None:
        def expire() -> None:
            if not self.valid:
                LOG.info("session %s never completed grantAccess, dropping", self.session_id)
                self.close()

        self._establish_timer = threading.Timer(timeout, expire)
        self._establish_timer.daemon = True
        self._establish_timer.start()

    def validate(self, uuid: str) -> None:
        if self._establish_timer is not None:
            self._establish_timer.cancel()
            self._establish_timer = None
        self.uuid = uuid
        self.valid = True

    def set_controlling(self, controlling: bool) -> None:
        was = self.controlling
        self.controlling = bool(controlling)
        if was != self.controlling and self.on_control_change is not None:
            self.on_control_change()
        if self._control_timer is not None:
            self._control_timer.cancel()
            self._control_timer = None
        if self.controlling:
            timer = threading.Timer(CONTROL_TIMEOUT, self._control_expired)
            timer.daemon = True
            self._control_timer = timer
            timer.start()

    def _control_expired(self) -> None:
        LOG.debug("control timer expired for %s", self.session_id)
        self.set_controlling(False)

    def send(self, data: str) -> bool:
        return self._send(data)

    def close(self) -> None:
        if self._control_timer is not None:
            self._control_timer.cancel()
        if self._establish_timer is not None:
            self._establish_timer.cancel()
        self._close()

    def __repr__(self) -> str:
        return f"<Session {self.session_id} uuid={self.uuid} valid={self.valid} ctrl={self.controlling}>"


class RobotApi:
    """Port of ``CommandReceiver`` + ``RobotApiHandler``.

    A connection must complete ``grantAccess`` before any motion or settings
    command is accepted, and motion commands are additionally ignored while the
    robot is pairing (mode 3) so the QR flow cannot be interrupted.
    """

    def __init__(
        self,
        events,
        state,
        mode_controller,
        wifi,
        central,
        updater=None,
        on_broadcast: Optional[Callable[[str], None]] = None,
    ) -> None:
        self.events = events
        self.state = state
        self.mode_controller = mode_controller
        self.wifi = wifi
        self.central = central
        self.updater = updater
        self.on_broadcast = on_broadcast or (lambda text: None)
        self.server = None

    def bind_server(self, server) -> None:
        self.server = server

    # -- framing --------------------------------------------------------------
    def handle_message(self, session: ClientSession, message: str) -> List[Dict[str, Any]]:
        """Feed one websocket text frame; returns the responses it produced."""
        if not message:
            return []
        session.buffer += message
        responses: List[Dict[str, Any]] = []
        # Frames are newline-delimited JSON; a frame holding a complete object
        # with no trailing newline is still dispatched so hand-rolled clients
        # (and the repo's own web_client.py) work.
        while True:
            if "\n" in session.buffer:
                line, _, session.buffer = session.buffer.partition("\n")
            elif _is_complete_json(session.buffer):
                line, session.buffer = session.buffer, ""
            else:
                break
            line = line.strip()
            if line:
                response = self.handle_line(session, line)
                if response is not None:
                    responses.append(response)
        return responses

    def handle_line(self, session: ClientSession, line: str) -> Optional[Dict[str, Any]]:
        if not line.startswith("{"):
            LOG.trace("dropping non-object line: %r", line[:80])
            return None
        try:
            payload = json.loads(line)
        except ValueError:
            LOG.debug("malformed json from %s: %r", session.session_id, line[:120])
            return None
        if not isinstance(payload, dict):
            return None

        command = Command(payload)
        cmd = command.cmd
        if not cmd:
            return None

        if cmd in AUTH_COMMANDS:
            return self._grant_access(session, payload)

        if not session.valid:
            LOG.info("refusing %r from unvalidated session %s", cmd, session.session_id)
            return None

        if cmd in NORMAL_COMMANDS:
            return self._handle_normal(session, cmd, payload)

        if cmd in MOTION_COMMANDS:
            if self.mode_controller is not None and self.mode_controller.get_mode() == 3:
                LOG.debug("%r ignored while pairing", cmd)
                return None
            self._handle_motion(session, cmd, Command(payload))
            return None

        LOG.debug("unknown client cmd %r", cmd)
        return None

    # -- auth -----------------------------------------------------------------
    def _grant_access(self, session: ClientSession, payload: Dict[str, Any]) -> Dict[str, Any]:
        seq = _as_int(payload.get("seq"), 0)
        uuid = payload.get("uuid")
        device_name = payload.get("device_name") or "unknown"
        if not uuid:
            # ``grantAccessToClient`` answers first, then ``closeConnection()``.
            session.close_after_send = True
            return self._fail("grantAccess", seq, ERROR_NO_UUID)

        known = self.state.is_paired(uuid)
        ap_mode = bool(self.wifi and self.wifi.is_ap_mode())
        pairing = self.mode_controller is not None and self.mode_controller.get_mode() == 3

        if not (ap_mode or known or pairing):
            session.close_after_send = True
            return self._fail("grantAccess", seq, ERROR_UNAUTHORIZED)

        if not known:
            self.state.add_client(uuid, device_name)

        session.device_name = device_name
        session.validate(uuid)
        # ``grantAccessToClient`` always routes through the pair controller's
        # success path, which is what plays the grant chime and leaves pair
        # mode -- calling the sound here as well would double it.
        if self.mode_controller is not None:
            self.mode_controller.success_connection_in_pair_mode()

        response = self._ok("grantAccess", seq)
        response["robot"] = self._robot_snapshot()
        return response

    # -- settings / info ------------------------------------------------------
    def _handle_normal(self, session: ClientSession, cmd: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        seq = _as_int(payload.get("seq"), 0)
        enable = bool(payload.get("enable", False))

        if cmd == "getWifiList":
            response = self._ok(cmd, seq)
            response["wifi_list"] = self.wifi.scan_results() if self.wifi else []
            response["currentSSID"] = self.wifi.current_ssid() if self.wifi else None
            return response

        if cmd == "connectWifi":
            return self._connect_wifi(session, payload, seq)

        if cmd == "face_detection":
            previous = self.state.face_detection
            self.state.face_detection = enable
            if previous != enable and self.central is not None:
                if enable:
                    self.central.start_face_detection()
                else:
                    self.central.stop_face_detection()
            return self._ok(cmd, seq)

        if cmd == "voice_recognition":
            previous = self.state.voice_recognition
            self.state.voice_recognition = enable
            if previous != enable and self.central is not None:
                if enable:
                    self.central.start_voice_recognition()
                else:
                    self.central.stop_voice_recognition()
            return self._ok(cmd, seq)

        if cmd == "mute":
            previous = self.state.mute
            self.state.mute = enable
            if previous != enable and self.central is not None:
                self.central.set_mute(enable)
            return self._ok(cmd, seq)

        if cmd == "power":
            if self.mode_controller is not None:
                self.mode_controller.wake()
            self.events.power_off()
            return self._ok(cmd, seq)

        if cmd == "user_control":
            # The original sends no reply for this command; the client only
            # observes the resulting mode broadcast.
            session.set_controlling(enable)
            return None

        if cmd == "change_name":
            new_name = payload.get("new_name")
            if not isinstance(new_name, str) or not new_name or len(new_name) > MAX_NAME_LENGTH:
                return self._fail(cmd, seq, ERROR_INVALID_NAME)
            self.state.name = new_name
            return self._ok(cmd, seq)

        if cmd == "paired_list":
            response = self._ok(cmd, seq)
            response["clients"] = self.state.clients
            return response

        if cmd == "unpair":
            return self._unpair(payload, seq)

        return self._fail(cmd, seq, ERROR_GENERIC)

    def _unpair(self, payload: Dict[str, Any], seq: int) -> Dict[str, Any]:
        uuid = payload.get("uuid")
        if not uuid:
            self.state.clear_clients()
            response = self._ok("unpair", seq)
            response["clients"] = self.state.clients
            self._clear_all_sessions()
            return response
        if not self.state.is_paired(uuid):
            return self._fail("unpair", seq, ERROR_CLIENT_NOT_FOUND)
        clients = self.state.remove_client(uuid)
        response = self._ok("unpair", seq)
        response["clients"] = clients
        self._close_session_by_uuid(uuid)
        return response

    def _connect_wifi(self, session: ClientSession, payload: Dict[str, Any], seq: int) -> Dict[str, Any]:
        ssid = payload.get("ssid")
        password = payload.get("wifi_pw")
        code = self.wifi.connect(ssid, password) if self.wifi else ERROR_GENERIC
        if code > 0:
            return self._fail("connectWifi", seq, code)
        if code == 0:
            return self._ok("connectWifi", seq)
        # -1: association is still in flight; the answer arrives asynchronously
        # as connectWifi on the same connection within 30 s.
        if self.wifi is not None:
            self.wifi.await_connection_result(
                on_success=lambda: self._send_to(session, self._ok("connectWifi", seq)),
                on_unauthorized=lambda: self._send_to(session, self._fail("connectWifi", seq, ERROR_WIFI_UNAUTHORIZED)),
                timeout=30.0,
            )
        return None

    # -- motion / hardware ----------------------------------------------------
    def _handle_motion(self, session: ClientSession, cmd: str, command: Command) -> None:
        if cmd == "move":
            self.events.move(command.power, command.angle)
            if command.power > 0 and command.angle == 0:
                timer = threading.Timer(0.1, self.events.move_head, args=(0,))
                timer.daemon = True
                timer.start()
        elif cmd == "move-head":
            self.events.move_head(command.angle)
        elif cmd == "head-dir":
            self.events.move_head_dir(command.dir)
        elif cmd == "projector":
            self.events.projector_mode(command.mode)
        elif cmd == "reset-wdt":
            self.events.reset()
        elif cmd == "d-head-power":
            self.events.change_head_dir_power(command.power)
        elif cmd == "d-leg-power":
            self.events.change_leg_power(command.power)
        elif cmd == "lcd":
            s = command.s if command.s != -1 else -1
            l = command.l if command.l != -1 else -1
            self.events.lcd(s, l)
        elif cmd == "led":
            self.events.led(command.r, command.b, command.y, command.g)
        elif cmd == "mode":
            self.events.mode(command.mode)
        elif cmd == "play_sound":
            self.events.play_sound(command.sound_id, command.interrupt == 1)
        elif cmd == "self_update":
            if command.url and self.state.battery > MIN_SELF_UPDATE_BATTERY and self.updater:
                LOG.info("self update now: %s", command.url)
                self.updater.update(command.url)
        elif cmd == "self_update_unsafe":
            if command.url and self.updater:
                LOG.info("self update (unsafe) now: %s", command.url)
                self.updater.update(command.url)
        elif cmd == "reset_mcu":
            self.events.reset_mcu()

    # -- response helpers -----------------------------------------------------
    def _ok(self, cmd: str, seq: int, **extra: Any) -> Dict[str, Any]:
        response: Dict[str, Any] = dict(extra)
        response["resultCode"] = OK
        response["cmd"] = cmd
        response["seq"] = seq
        return response

    def _fail(self, cmd: str, seq: int, code: int, **extra: Any) -> Dict[str, Any]:
        response = self._ok(cmd, seq, **extra)
        response["resultCode"] = code
        return response

    def send_response(self, session: ClientSession, response: Optional[Dict[str, Any]]) -> None:
        if response is None:
            return
        self._send_to(session, response)

    @staticmethod
    def _send_to(session: ClientSession, response: Dict[str, Any]) -> None:
        # The app writes ``jsonObject.toString() + "\n"`` straight into the text
        # frame, so the payload keeps a trailing newline.
        session.send(json.dumps(response, separators=(",", ":"), ensure_ascii=False) + "\n")

    def _robot_snapshot(self) -> Dict[str, Any]:
        if self.central is None:
            mode = 1
        else:
            mode = self.mode_controller.get_mode() if self.mode_controller is not None else 1
        from .models import Robot

        robot = Robot(
            self.state,
            mode=mode,
            ip=self.wifi.local_ip() if self.wifi else "",
            ap_mode=bool(self.wifi and self.wifi.is_ap_mode()),
            ssid=self.wifi.current_ssid() if self.wifi else None,
        )
        return robot.to_dict()

    # -- session registry hooks (bound by the server) ------------------------
    def _clear_all_sessions(self) -> None:
        if self.server is not None:
            self.server.clear_all()

    def _close_session_by_uuid(self, uuid: str) -> None:
        if self.server is not None:
            self.server.close_client(uuid)


def _as_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _is_complete_json(text: str) -> bool:
    text = text.strip()
    if not text.startswith("{") or not text.endswith("}"):
        return False
    try:
        json.loads(text)
        return True
    except ValueError:
        return False
