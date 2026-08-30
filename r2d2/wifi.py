from __future__ import annotations

import re
import socket
import subprocess
import threading
from typing import Callable, Dict, List, Optional

# ``connectWifi`` return codes are echoed straight into ``resultCode`` by the
# command router, so they are imported from where the wire constants live.
from .api import (
    ERROR_WIFI_NOT_FOUND,
    ERROR_WIFI_UNSUPPORTED,
)
from .log import get_logger

LOG = get_logger("wifi")

AP_SSID_PREFIX = "R2D2-Router"
AP_PASSPHRASE = "00000000"
AP_IP = "192.168.43.1"
AP_MODE_CHECK_INTERVAL = 1.0

WIFI_RESULT_ASYNC = -1
WIFI_RESULT_SUCCESS = 0


class WifiService:
    """Linux port of ``WIFI/WifiService``.

    Android's ``WifiManager``/``WifiApControl`` have no Python equivalent, so
    this drives NetworkManager (``nmcli``) instead: ``dev wifi list`` for the
    scan results, ``dev wifi connect`` for provisioning, and
    ``dev wifi hotspot`` for the ``R2D2-Router`` access-point mode the pairing
    flow depends on. Every command is wrapped, so a host without
    NetworkManager degrades to "no wifi" rather than crashing the robot.
    """

    def __init__(self, state=None, mock: bool = False, interface: str = "wlan0") -> None:
        self.state = state
        self.mock = mock
        self.interface = interface
        self.is_processing = False
        self._is_ap_mode = False
        self._lock = threading.RLock()
        self._listeners: List[Dict[str, Callable[[], None]]] = []
        self._check_timer: Optional[threading.Timer] = None
        self._last_network_state: Optional[str] = None
        self._scan_cache: List[Dict[str, object]] = []
        self._security: Dict[str, str] = {}
        self._mock_network_connected = True
        self.events: Optional[object] = None

        if state is not None:
            self._is_ap_mode = bool(state.ap_mode)

    # -- subprocess plumbing --------------------------------------------------
    def _run(self, args: List[str], timeout: float = 20.0) -> Optional[str]:
        if self.mock:
            return None
        try:
            completed = subprocess.run(
                args,
                capture_output=True,
                text=True,
                timeout=timeout,
                check=False,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            LOG.debug("%s failed: %s", " ".join(args), exc)
            return None
        if completed.returncode != 0:
            LOG.debug("%s -> %s: %s", " ".join(args), completed.returncode, completed.stderr.strip())
            return None
        return completed.stdout

    # -- queries --------------------------------------------------------------
    def is_ap_mode(self) -> bool:
        return self._is_ap_mode

    def is_ap_mode_connecting(self) -> bool:
        return self.is_processing

    def network_connected(self) -> bool:
        if self.mock:
            return self._mock_network_connected
        out = self._run(["nmcli", "-t", "-f", "STATE", "dev", "show", self.interface])
        return bool(out) and "connected" in out

    def current_ssid(self) -> Optional[str]:
        if self._is_ap_mode:
            return self.ap_ssid()
        if self.mock:
            return None if self.state is None else self.state.ssid
        out = self._run(["nmcli", "-t", "-f", "active", "dev", "wifi"])
        if out:
            value = out.strip().strip(":")
            if value and value.lower() not in {"--", "none", ""}:
                return value
        return None if self.state is None else self.state.ssid

    def ap_ssid(self) -> str:
        name = None if self.state is None else self.state.name
        return f"{AP_SSID_PREFIX} {name}" if name else AP_SSID_PREFIX

    def local_ip(self) -> str:
        if self._is_ap_mode:
            return AP_IP
        if self.mock:
            return "127.0.0.1"
        try:
            probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                probe.connect(("8.8.8.8", 80))
                return probe.getsockname()[0]
            finally:
                probe.close()
        except OSError:
            return "127.0.0.1"

    def broadcast_address(self) -> str:
        return "192.168.43.255" if self._is_ap_mode else "255.255.255.255"

    # -- scanning -------------------------------------------------------------
    def start_scan(self) -> None:
        self._scan_cache = self.scan_results()

    def scan_results(self) -> List[Dict[str, object]]:
        """``getScanResult()`` — ``[{"ssid": str, "rssi": int}]``, strongest first.

        ``Model/Wifi`` carries only ``ssid``/``rssi``, so the security column we
        read from ``nmcli`` goes to a side table instead of leaking into the
        JSON the console sees. ``WifiService.getNetworkCapabilities`` uses that
        same information to reject networks it cannot configure.
        """
        if self.mock:
            return list(self._scan_cache)
        out = self._run(
            ["nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY", "dev", "wifi", "list", "--rescan", "yes"],
            timeout=30.0,
        )
        if out is None:
            return []
        seen: Dict[str, int] = {}
        for line in out.splitlines():
            fields = [f.replace("\\:", ":") for f in line.split(":")]
            if len(fields) < 2:
                continue
            ssid = fields[0].strip()
            if not ssid:
                continue
            try:
                rssi = int(re.sub(r"[^0-9]", "", fields[1]) or 0)
            except ValueError:
                rssi = 0
            if len(fields) > 2:
                self._security[ssid] = fields[2].strip()
            # Android reports quality 0-100; the wire format keeps that meaning.
            if ssid not in seen or rssi > seen[ssid]:
                seen[ssid] = rssi
        results = [{"ssid": k, "rssi": v} for k, v in seen.items()]
        results.sort(key=lambda item: item["rssi"], reverse=True)
        self._scan_cache = results
        return results

    def network_security(self, ssid: str) -> str:
        return self._security.get(ssid, "")

    @staticmethod
    def _is_configurable(caps: str) -> bool:
        """``WPA``/``WEP`` are the only capability sets the app can configure."""
        upper = caps.upper()
        return "WPA" in upper or "WEP" in upper or upper in {"", "--"}

    # -- association ----------------------------------------------------------
    def connect(self, ssid: Optional[str], password: Optional[str]) -> int:
        """Kick off an association; mirrors ``connectWifi`` return codes.

        ``WifiService.connectToSavedWifiSetting`` returns 414 when the SSID is
        not in the scan results, 410 for an unsupported security type, 412 when
        the config cannot be added, and -1 for "association started -- listen
        for the state broadcast". It has no busy guard, so neither does this.
        """
        if not ssid:
            LOG.info("no ssid supplied")
            return ERROR_WIFI_NOT_FOUND
        known = any(entry["ssid"] == ssid for entry in self._scan_cache) if self._scan_cache else True
        if not known:
            LOG.info("ssid %s not in scan results", ssid)
            return ERROR_WIFI_NOT_FOUND
        # ``WifiService.connectToSavedWifiSetting`` refuses to build a config for
        # a network whose capabilities are neither WPA nor WEP.
        caps = self.network_security(ssid)
        if self._security and caps and not self._is_configurable(caps):
            LOG.info("ssid %s has unsupported security %r", ssid, caps)
            return ERROR_WIFI_UNSUPPORTED
        with self._lock:
            self.is_processing = True

        if self._is_ap_mode:
            self.stop_ap_mode()

        if self.mock:
            self.is_processing = False
            return WIFI_RESULT_ASYNC

        def worker() -> None:
            out = self._run(
                ["nmcli", "dev", "wifi", "connect", ssid, "password", password or ""],
                timeout=45.0,
            )
            with self._lock:
                self.is_processing = False
            if out is None:
                self._emit("on_unauthorized")
            else:
                if self.state is not None:
                    self.state.ssid = ssid
                self._emit("on_connected")

        threading.Thread(target=worker, name="wifi-connect", daemon=True).start()
        return WIFI_RESULT_ASYNC

    def await_connection_result(
        self,
        on_success: Callable[[], None],
        on_unauthorized: Callable[[], None],
        timeout: float = 30.0,
        on_timeout: Optional[Callable[[], None]] = None,
    ) -> None:
        """The async ``WifiStateMachine`` broadcast the app listens for."""
        entry = {
            "on_connected": on_success,
            "on_unauthorized": on_unauthorized,
            "oneshot": True,
        }
        with self._lock:
            self._listeners.append(entry)

        def expire() -> None:
            with self._lock:
                still_waiting = entry in self._listeners
                if still_waiting:
                    self._listeners.remove(entry)
            if still_waiting and on_timeout is not None:
                on_timeout()

        timer = threading.Timer(timeout, expire)
        timer.daemon = True
        timer.start()

    def remove_listener(self, entry: Dict[str, Callable[[], None]]) -> None:
        with self._lock:
            if entry in self._listeners:
                self._listeners.remove(entry)

    def _emit(self, event: str) -> None:
        with self._lock:
            listeners = list(self._listeners)
        for entry in listeners:
            callback = entry.get(event)
            if callback is None:
                continue
            try:
                callback()
            except Exception:  # pragma: no cover
                LOG.exception("wifi listener %s raised", event)
            if entry.get("oneshot"):
                self.remove_listener(entry)

    # -- access point ---------------------------------------------------------
    def ap_mode_toggle(self) -> None:
        if self.is_processing:
            LOG.info("drop ap mode toggle command")
            return
        if self._is_ap_mode:
            self.change_to_wifi_mode()
        else:
            self.change_to_ap_mode()

    def change_to_ap_mode(self) -> None:
        self._stop_connection()
        LOG.info("changing to ap mode")
        with self._lock:
            self.is_processing = True
            self._is_ap_mode = True
        if self.state is not None:
            self.state.ap_mode = True
        self._run(["nmcli", "dev", "wifi", "hotspot", "ssid", self.ap_ssid(), "password", AP_PASSPHRASE], timeout=45.0)
        self.is_processing = False
        self._start_ap_check()
        if self.events is not None:
            self.events.restore_light()

    def change_to_wifi_mode(self) -> int:
        self._stop_connection()
        LOG.info("change to wifi mode")
        with self._lock:
            self._is_ap_mode = False
            self.is_processing = True
        if self.state is not None:
            self.state.ap_mode = False
        self._stop_ap_check()
        self._run(["nmcli", "dev", "wifi", "hotspot", "stop"], timeout=20.0)
        self._run(["nmcli", "radio", "all", "on"], timeout=10.0)
        if self.events is not None:
            self.events.restore_light()
        result = self.connect(self.state.ssid if self.state else None, None)
        self.is_processing = False
        return result

    def stop_ap_mode(self) -> None:
        self._stop_ap_check()
        self._run(["nmcli", "dev", "wifi", "hotspot", "stop"], timeout=20.0)

    def _stop_connection(self) -> None:
        self._run(["nmcli", "dev", "disconnect", self.interface], timeout=15.0)

    def _start_ap_check(self) -> None:
        # ``APModeCheckingTask`` re-checks the AP every second for 15 s and
        # reboots the process if the AP never comes up; on Linux the hotspot
        # command is synchronous, so this only confirms and logs.
        self._stop_ap_check()
        timer = threading.Timer(AP_MODE_CHECK_INTERVAL, self._check_ap_once, args=(0,))
        timer.daemon = True
        self._check_timer = timer
        timer.start()

    def _check_ap_once(self, counter: int) -> None:
        if counter > 14:
            LOG.warning("AP mode did not come up")
            return
        out = self._run(["nmcli", "-t", "-f", "TYPE,STATE", "dev", "show", self.interface])
        if out and "hotspot" in out.replace("\\:", ":"):
            LOG.info("AP mode active: %s", self.ap_ssid())
            return
        timer = threading.Timer(AP_MODE_CHECK_INTERVAL, self._check_ap_once, args=(counter + 1,))
        timer.daemon = True
        self._check_timer = timer
        timer.start()

    def _stop_ap_check(self) -> None:
        with self._lock:
            if self._check_timer is not None:
                self._check_timer.cancel()
                self._check_timer = None

    def close(self) -> None:
        self._stop_ap_check()
