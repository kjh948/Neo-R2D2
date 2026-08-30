from __future__ import annotations

import json
import socket
import threading
from typing import Callable, Optional

from .log import get_logger

LOG = get_logger("discovery")

SERVER_PORT = 8090
BROADCAST_INTERVAL = 3.0
AP_IP = "192.168.43.1"
PAIR_MODE = 3


class UDPDiscovery:
    """Port of ``UDP/UDPBroadcastService``.

    The phone app is told where the robot is by a 3 s broadcast of
    ``{"cmd":"updBroadcast","ip","uuid","name","ap_mode"}`` to port 8090. When
    the robot itself is in AP mode the socket is *bound* to ``192.168.43.1`` so
    the datagram leaves the access-point interface rather than the LAN one.
    While the robot is in pair mode the ephemeral QR key rides along as
    ``"key"``, which is how the client that displayed the code learns that its
    own network was joined.
    """

    def __init__(
        self,
        state,
        wifi: Optional[object] = None,
        port: int = SERVER_PORT,
        interval: float = BROADCAST_INTERVAL,
        pair_key: Optional[Callable[[], Optional[str]]] = None,
        mode: Optional[Callable[[], int]] = None,
    ) -> None:
        self.state = state
        self.wifi = wifi
        self.port = port
        self.interval = interval
        self._pair_key = pair_key
        self._mode = mode
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._socket: Optional[socket.socket] = None

    def build_message(self) -> str:
        ap_mode = bool(self.wifi.is_ap_mode()) if self.wifi is not None else bool(self.state.ap_mode)
        ip = self.wifi.local_ip() if self.wifi is not None else ""
        payload = {
            "cmd": "updBroadcast",
            "ip": ip,
            "uuid": self.state.udid,
            "name": self.state.name,
            "ap_mode": ap_mode,
        }
        if self._pair_key is not None and self._mode is not None and self._mode() == PAIR_MODE:
            payload["key"] = self._pair_key()
        return json.dumps(payload, separators=(",", ":"), ensure_ascii=False)

    def start(self) -> None:
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._loop, name="udp-discovery", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        thread, self._thread = self._thread, None
        if thread is not None and thread.is_alive():
            thread.join(timeout=2.0)
        if self._socket is not None:
            self._socket.close()
            self._socket = None

    def _loop(self) -> None:
        LOG.info("UDP Broadcast service started")
        while not self._stop.is_set():
            self._broadcast()
            self._stop.wait(self.interval)

    def _broadcast(self) -> None:
        ap_mode = bool(self.wifi.is_ap_mode()) if self.wifi is not None else bool(self.state.ap_mode)
        try:
            if self._socket is None:
                self._socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                self._socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                if ap_mode:
                    # Bind to the AP address so the broadcast egresses wlan0 in
                    # AP mode; failure falls back to the default route.
                    try:
                        self._socket.bind((AP_IP, self.port))
                    except OSError:
                        self._socket.bind(("", self.port))
            target = self.wifi.broadcast_address() if self.wifi is not None else "255.255.255.255"
            self._socket.sendto(self.build_message().encode("utf-8"), (target, self.port))
        except OSError as exc:
            LOG.debug("udp broadcast failed: %s", exc)
            if self._socket is not None:
                try:
                    self._socket.close()
                except OSError:
                    pass
                self._socket = None
