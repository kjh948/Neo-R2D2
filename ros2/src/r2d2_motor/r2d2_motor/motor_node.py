"""ROS 2 node: ``geometry_msgs/Twist`` on ``/cmd_vel`` -> R2-D2 MCU ``move`` frames.

Behaviour (agreed with the operator):
* UART direct — the node owns ``/dev/ttyS2`` via the repo's ``r2d2`` package;
  the r2d2 app must NOT run at the same time.
* Differential-style mapping: dominant ``linear.x`` -> forward/reverse,
  dominant ``angular.z`` -> lateral strafe (see ``twist_mapping``).
* While driving, the frame is repeated every ``repeat_period`` (the Android
  app drives its joystick the same way, 300 ms).
* Safety: if no Twist arrives for ``cmd_vel_timeout`` seconds a
  ``{"cmd":"move","power":0}`` stop frame is sent exactly once.
"""
from __future__ import annotations

import logging
import threading

import rclpy
from geometry_msgs.msg import Twist
from rclpy.node import Node

from .mcu_client import McuClient
from .mcu_config import load_mcu_config
from .twist_mapping import MoveMapping, holonomic_to_move, tank_to_move, twist_to_move


class MotorNode(Node):
    def __init__(self) -> None:
        super().__init__("r2d2_motor")

        # "" / -1 mean "take it from the r2d2 config file (config_local.json),
        # falling back to the built-in default" — explicit params still win.
        self.declare_parameter("config_file", "")
        self.declare_parameter("serial_port", "")
        self.declare_parameter("baudrate", -1)
        self.declare_parameter("serial_read_timeout", -1.0)
        self.declare_parameter("mock", False)
        self.declare_parameter("cmd_vel_topic", "/cmd_vel")
        self.declare_parameter("max_linear_velocity", 0.5)
        self.declare_parameter("max_angular_velocity", 1.0)
        self.declare_parameter("deadband", 0.02)
        self.declare_parameter("min_power", 1)
        self.declare_parameter("invert_strafe", False)
        self.declare_parameter("mapping_mode", "differential")
        self.declare_parameter("cmd_vel_timeout", 0.5)
        self.declare_parameter("repeat_period", 0.3)
        self.declare_parameter("gin_period", 5.0)

        cfg = load_mcu_config(self.get_parameter("config_file").value)
        if "_config_path" in cfg:
            self.get_logger().info(f"using MCU defaults from {cfg['_config_path']}")
        else:
            self.get_logger().warn("no r2d2 config found; falling back to built-in defaults")

        def resolve(name: str, key: str, fallback):
            """Explicit ROS param wins; "" / -1 defer to the config file."""
            value = self.get_parameter(name).value
            if isinstance(value, str) and value == "":
                return cfg.get(key, fallback)
            if isinstance(value, (int, float)) and not isinstance(value, bool) and value < 0:
                return cfg.get(key, fallback)
            return value

        serial_port = resolve("serial_port", "serial_port", "/dev/ttyS2")
        baudrate = int(resolve("baudrate", "baudrate", 115200))
        read_timeout = float(resolve("serial_read_timeout", "serial_read_timeout", 1.0))
        mock = bool(self.get_parameter("mock").value or cfg.get("mock_serial", False))

        mode = str(self.get_parameter("mapping_mode").value).strip().lower()
        if mode not in ("differential", "holonomic", "tank"):
            raise ValueError(f"unknown mapping_mode {mode!r} (differential|holonomic|tank)")
        self._mode = mode

        self._mapping = MoveMapping(
            max_linear_velocity=self.get_parameter("max_linear_velocity").value,
            max_angular_velocity=self.get_parameter("max_angular_velocity").value,
            deadband=self.get_parameter("deadband").value,
            min_power=int(self.get_parameter("min_power").value),
            invert_strafe=self.get_parameter("invert_strafe").value,
        )
        self._timeout = float(self.get_parameter("cmd_vel_timeout").value)
        repeat_period = float(self.get_parameter("repeat_period").value)

        self._client = McuClient(
            device=serial_port,
            mock=mock,
            baudrate=baudrate,
            read_timeout=read_timeout,
            logger=self.get_logger(),
        )
        try:
            self._client.open()
        except Exception as exc:
            self.get_logger().error(f"cannot open {self._client.transport.device}: {exc}")
            raise

        self._lock = threading.Lock()
        self._last_twist: Twist | None = None
        self._last_rx = self.get_clock().now()
        self._driving = False          # a non-zero move is live on the MCU

        topic = self.get_parameter("cmd_vel_topic").value
        self.create_subscription(Twist, topic, self._on_twist, 10)
        self.create_timer(repeat_period, self._tick)
        gin_period = float(self.get_parameter("gin_period").value)
        if gin_period > 0.0:
            self.create_timer(gin_period, self._poll_gin)

        self.get_logger().info(
            f"r2d2_motor ready: {topic} -> MCU move over {self._client.transport.device}"
            + (" [MOCK]" if self.get_parameter("mock").value else "")
        )

    # -- inbound ROS ----------------------------------------------------------
    def _on_twist(self, msg: Twist) -> None:
        with self._lock:
            self._last_twist = msg
            self._last_rx = self.get_clock().now()

    # -- periodic send --------------------------------------------------------
    def _tick(self) -> None:
        now = self.get_clock().now()
        with self._lock:
            twist = self._last_twist
            stale = (now - self._last_rx).nanoseconds > self._timeout * 1e9

        if twist is None:
            return

        if self._mode == "holonomic":
            power, angle = holonomic_to_move(twist.linear.x, twist.linear.y, self._mapping)
        elif self._mode == "tank":
            power, angle = tank_to_move(twist.linear.x, twist.angular.z, self._mapping)
        else:
            power, angle = twist_to_move(twist.linear.x, twist.angular.z, self._mapping)

        if power == 0 or stale:
            if self._driving:
                reason = "cmd_vel timed out" if stale else "twist is zero"
                self.get_logger().info(f"stopping ({reason})")
                self._send_move(0, angle=0)
                self._driving = False
            return

        # Repeat while driving: matches the Android app's 300 ms joystick tick
        # and keeps the MCU fed even if the teleop publisher goes quiet.
        self._send_move(power, angle)
        self._driving = True

    def _poll_gin(self) -> None:
        self._client.request_gin()

    def _send_move(self, power: int, angle: int) -> None:
        if not self._client.move(power, angle):
            self.get_logger().warn(f"move(power={power}, angle={angle}) failed to send")

    # -- shutdown ---------------------------------------------------------------
    def destroy_node(self) -> bool:
        if self._driving:
            self._client.stop()
            self._driving = False
        self._client.close()
        return super().destroy_node()


def main(args=None) -> None:
    # Surface the vendored r2d2_link logger (UART tx in mock mode, write errors).
    logging.basicConfig(level=logging.INFO, format="[uart] %(message)s")
    rclpy.init(args=args)
    node = MotorNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


if __name__ == "__main__":
    main()
