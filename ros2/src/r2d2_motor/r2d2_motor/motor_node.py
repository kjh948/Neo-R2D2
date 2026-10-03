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
from .twist_mapping import MoveMapping, twist_to_move


class MotorNode(Node):
    def __init__(self) -> None:
        super().__init__("r2d2_motor")

        self.declare_parameter("serial_port", "/dev/ttyS2")
        self.declare_parameter("mock", False)
        self.declare_parameter("cmd_vel_topic", "/cmd_vel")
        self.declare_parameter("max_linear_velocity", 0.5)
        self.declare_parameter("max_angular_velocity", 1.0)
        self.declare_parameter("deadband", 0.02)
        self.declare_parameter("min_power", 1)
        self.declare_parameter("invert_strafe", False)
        self.declare_parameter("cmd_vel_timeout", 0.5)
        self.declare_parameter("repeat_period", 0.3)
        self.declare_parameter("gin_period", 5.0)

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
            device=self.get_parameter("serial_port").value,
            mock=self.get_parameter("mock").value,
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
