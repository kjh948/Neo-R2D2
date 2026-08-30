from __future__ import annotations

import json
import threading
from typing import Dict, Optional

from .api import ClientSession, RobotApi
from .log import get_logger
from .models import Robot
from .ws import WebSocketServer, WebSocketSession

LOG = get_logger("server")


class RobotServer:
    """Port of ``WebSocket/SocketServer`` — the controller-facing endpoint.

    One :class:`ClientSession` is created per accepted socket and lives in a
    registry until the client disconnects. The app keys sessions by the peer
    *hostname*, so a client that reconnects from the same address silently
    evicts its previous (stale) session; that behaviour is reproduced here
    because the web console relies on it after a page reload.

    Two timer families matter for correctness: every ``{"cmd":"gin","robot":{…}}``
    state push is broadcast to *all* connections, and the 10 s unpaired-socket
    timer plus the 12 s control-release timer are owned by ``ClientSession``.
    """

    def __init__(
        self,
        api: RobotApi,
        host: str = "0.0.0.0",
        port: int = 8887,
        path: str = "/",
        connection_lost_timeout: int = 5,
    ) -> None:
        self.api = api
        self.host = host
        self.port = port
        self._ws = WebSocketServer(
            host=host,
            port=port,
            path=path,
            on_message=self._on_message,
            on_connect=self._on_connect,
            on_disconnect=self._on_disconnect,
        )
        self._sessions: Dict[int, ClientSession] = {}
        self._by_host: Dict[str, ClientSession] = {}
        self._lock = threading.RLock()
        api.bind_server(self)

    def start(self) -> None:
        self._ws.start()

    def stop(self) -> None:
        self._ws.stop()
        with self._lock:
            self._sessions.clear()
            self._by_host.clear()

    @property
    def bound_port(self) -> Optional[int]:
        return self._ws.bound_port

    # -- websocket callbacks --------------------------------------------------
    def _on_connect(self, connection: WebSocketSession) -> None:
        host = connection.address[0] if connection.address else "?"
        LOG.info("%s entered the room!", host)
        previous = self._by_host.get(host)
        if previous is not None:
            LOG.info("Same source connected, closed old connection")
            self._teardown(previous)
            try:
                previous.close()
            except Exception:  # pragma: no cover
                pass

        key = id(connection)

        def forget() -> None:
            """Deregister immediately, without waiting for the reader thread.

            A blocked ``recv()`` is not guaranteed to notice a locally-closed
            socket, so a session the server itself drops (establish timeout,
            unpair, host eviction) leaves the registry here as well as closing
            the transport; ``_on_disconnect`` remains for peer-initiated exits.
            """
            with self._lock:
                self._sessions.pop(key, None)
                if self._by_host.get(host) is session:
                    self._by_host.pop(host, None)
            connection.close()

        session = ClientSession(
            session_id=f"{host}:{connection.address[1] if connection.address else 0}",
            send=connection.send_text,
            close=forget,
            on_control_change=self.check_control_mode_needed,
        )
        session.start_establish_timer()
        with self._lock:
            self._sessions[key] = session
            self._by_host[host] = session

    def _on_message(self, connection: WebSocketSession, message: str) -> None:
        with self._lock:
            session = self._sessions.get(id(connection))
        if session is None:
            connection.close()
            return
        responses = self.api.handle_message(session, message)
        for response in responses:
            session.send(_compact(response))
        if session.close_after_send:
            session.close()

    def _on_disconnect(self, connection: WebSocketSession) -> None:
        with self._lock:
            session = self._sessions.pop(id(connection), None)
            if session is not None:
                if self._by_host.get(connection.address[0]) is session:
                    self._by_host.pop(connection.address[0], None)
        if session is not None:
            self._teardown(session)
        self.check_control_mode_needed()

    def _teardown(self, session: ClientSession) -> None:
        if session.controlling:
            session.controlling = False
            self.check_control_mode_needed()

    # -- fan-out --------------------------------------------------------------
    def send(self, text: str) -> int:
        """``SocketServer.send`` — every registered connection, newline kept."""
        with self._lock:
            sessions = list(self._sessions.values())
        return sum(1 for session in sessions if session.send(text))

    def notify_robot_changed(self, mode: Optional[int] = None, ip: str = "", ap_mode: bool = False,
                             ssid: Optional[str] = None) -> int:
        """``SharedUtils.notifyRobotChanged``: broadcast ``{"cmd":"gin", "robot":{…}}``."""
        state = self.api.state
        robot = Robot(state, mode=self.api.mode_controller.get_mode() if mode is None and self.api.mode_controller else (mode or 1),
                      ip=ip, ap_mode=ap_mode, ssid=ssid)
        return self.send(_compact({"cmd": "gin", "robot": robot.to_dict()}) + "\n")

    def clear_all(self) -> int:
        with self._lock:
            sessions = list(self._sessions.values())
            self._sessions.clear()
            self._by_host.clear()
        for session in sessions:
            try:
                session.close()
            except Exception:  # pragma: no cover
                pass
        return len(sessions)

    def close_client(self, uuid: str) -> bool:
        with self._lock:
            target = next((s for s in self._sessions.values() if s.uuid == uuid), None)
        if target is None:
            return False
        target.close()
        return True

    # -- control arbitration --------------------------------------------------
    def get_controlling_num(self) -> int:
        with self._lock:
            return sum(1 for session in self._sessions.values() if session.controlling)

    def check_control_mode_needed(self) -> None:
        """The app's rule: a controller holding the stick forces READY mode.

        ``user_control:{"enable":true}`` starts a 12 s window; any command the
        client sends re-arms it, and when it lapses the robot returns to
        autonomous behaviour. ``ModeController`` is driven from here so that the
        mode machine, not the client, owns the transition.
        """
        controlling = self.get_controlling_num()
        LOG.debug("Number of user controlling: %d", controlling)
        controller = self.api.mode_controller
        if controller is None:
            return
        if controlling > 0:
            controller.start_user_control_mode()
        else:
            controller.stop_user_control_mode()

    @property
    def session_count(self) -> int:
        with self._lock:
            return len(self._sessions)


def _compact(payload: Dict[str, object]) -> str:
    return json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
