"""Bridge between the ROS node and the MCU UART link (``r2d2_link``)."""
from __future__ import annotations

import json

from .r2d2_link import Commander, JsonLineTransport


# r2d2/state.py:14-16 CHARGING_* constants
_CHARGING_NAMES = {0: "NOT_CHARGED", 1: "CHARGING", 2: "CHARGING_FINISHED"}


class McuStatus:
    """Minimal state the Commander needs.

    ``Commander`` only reads ``.charging`` for its locomotion interlock, so we
    avoid the full file-backed ``RobotState`` (and its side effects on state.json).
    """

    def __init__(self) -> None:
        self.charging = 0
        self.battery = -1


class McuClient:
    """Owns the UART link; exposes the handful of commands the node needs."""

    def __init__(
        self,
        device: str,
        mock: bool = False,
        baudrate: int = 115200,
        read_timeout: float = 1.0,
        logger=None,
    ) -> None:
        self._log = logger
        self.status = McuStatus()
        self.transport = JsonLineTransport(
            device=device, baudrate=baudrate, read_timeout=read_timeout,
            mock=mock, on_line=self._on_line,
        )
        self.commander = Commander(self.transport, state=self.status)

    # -- lifecycle ------------------------------------------------------------
    def open(self) -> None:
        self.transport.open()
        # Mirrors MainActivity: announce software readiness to the MCU.
        self.commander.software_ready()

    def close(self) -> None:
        self.transport.close()

    @property
    def is_open(self) -> bool:
        return self.transport.is_open

    # -- commands -------------------------------------------------------------
    def move(self, power: int, angle: int) -> bool:
        return self.commander.move(power, angle)

    def stop(self) -> bool:
        return self.commander.move(0, 0)

    def request_gin(self) -> bool:
        return self.commander.gin()

    # -- inbound frames ---------------------------------------------------------
    def _on_line(self, text: str) -> None:
        """Consume only the ``gin`` status frame; everything else is not ours."""
        if not text.startswith("{"):
            return
        try:
            data = json.loads(text)
        except ValueError:
            return
        if not isinstance(data, dict) or data.get("cmd") != "gin":
            return
        charging = int(data.get("charging-status", 0) or 0)
        if charging != self.status.charging:
            self.status.charging = charging
            if self._log is not None:
                # rclpy loggers take ONE string (no printf args — that raised
                # TypeError until the first real dock transition, 2026-10-25).
                # MCU mirrors Commander.java: ANY non-zero state suppresses
                # move frames (1=charging, 2=finished-but-still-docked).
                name = _CHARGING_NAMES.get(charging, f"unknown state {charging}")
                self._log.info(
                    f"MCU charging status -> {charging} ({name}) — locomotion "
                    + ("suppressed while docked" if charging else "allowed again")
                )
        batt = data.get("batt")
        if batt is not None:
            self.status.battery = int(batt)
