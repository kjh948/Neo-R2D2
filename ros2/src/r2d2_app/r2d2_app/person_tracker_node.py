"""ROS 2 node that follows a YOLO person track with the head and robot base."""
from __future__ import annotations

import math
import time

import rclpy
from geometry_msgs.msg import Twist
from rclpy.node import Node
from r2d2_msgs.msg import McuStatus
from r2d2_msgs.srv import SetHeadAngle
from std_msgs.msg import Int32
from yolo_msgs.msg import DetectionArray

from .person_tracker import PersonDetection, PersonTrackerController


class PersonTrackerNode(Node):
    def __init__(self) -> None:
        super().__init__("person_tracker")

        self.declare_parameter("detection_topic", "/yolo/tracking")
        self.declare_parameter("head_shift_topic", "/r2d2/head_shift")
        self.declare_parameter("head_center_service", "/r2d2/set_head_angle")
        self.declare_parameter("status_topic", "/r2d2/status")
        self.declare_parameter("cmd_vel_topic", "/cmd_vel")
        self.declare_parameter("control_rate", 10.0)
        self.declare_parameter("image_width", 640)
        self.declare_parameter("camera_hfov_deg", 80.0)
        self.declare_parameter("head_deadband_ratio", 0.03)
        self.declare_parameter("head_follow_gain", 0.5)
        self.declare_parameter("max_head_shift_deg", 5)
        self.declare_parameter("head_limit_deg", 30.0)
        self.declare_parameter("head_recenter_deg", 10.0)
        self.declare_parameter("base_rotation_speed", 0.15)
        self.declare_parameter("base_rotation_sign", 1)
        self.declare_parameter("target_lost_timeout", 0.7)

        get = self.get_parameter
        control_rate = float(get("control_rate").value)
        if not math.isfinite(control_rate) or control_rate <= 0.0:
            raise ValueError("control_rate must be finite and positive")
        self.controller = PersonTrackerController(
            image_width=int(get("image_width").value),
            camera_hfov_deg=float(get("camera_hfov_deg").value),
            head_deadband_ratio=float(get("head_deadband_ratio").value),
            head_follow_gain=float(get("head_follow_gain").value),
            max_head_shift_deg=int(get("max_head_shift_deg").value),
            head_limit_deg=float(get("head_limit_deg").value),
            head_recenter_deg=float(get("head_recenter_deg").value),
            base_rotation_speed=float(get("base_rotation_speed").value),
            base_rotation_sign=int(get("base_rotation_sign").value),
            target_lost_timeout=float(get("target_lost_timeout").value),
        )

        self._head_ready = False
        self._charging = False
        self._center_pending = False
        self._last_center_attempt = 0.0
        self._center_retry_interval = 2.0
        self._last_state = "lost"
        self._head_shift_pub = self.create_publisher(
            Int32, str(get("head_shift_topic").value), 10)
        self._cmd_vel_pub = self.create_publisher(
            Twist, str(get("cmd_vel_topic").value), 10)
        self._head_center_client = self.create_client(
            SetHeadAngle, str(get("head_center_service").value))
        self.create_subscription(
            DetectionArray, str(get("detection_topic").value), self._on_detections, 10)
        self.create_subscription(
            McuStatus, str(get("status_topic").value), self._on_status, 10)
        self.create_timer(1.0 / control_rate, self._control_tick)

        self.get_logger().info(
            f"person tracker ready: detections={get('detection_topic').value}, "
            f"cmd_vel={get('cmd_vel_topic').value}; waiting to center head")

    def _on_detections(self, msg: DetectionArray) -> None:
        detections = []
        for detection in msg.detections:
            if detection.class_name.strip().lower() != "person" or not detection.id:
                continue
            size_x = float(detection.bbox.size.x)
            size_y = float(detection.bbox.size.y)
            detections.append(PersonDetection(
                track_id=str(detection.id),
                center_x=float(detection.bbox.center.position.x),
                area=size_x * size_y,
                score=float(detection.score),
            ))
        self.controller.update(detections, time.monotonic())

    def _on_status(self, msg: McuStatus) -> None:
        charging = int(msg.charging) != 0
        if charging == self._charging:
            return
        self._charging = charging
        self._head_ready = False
        if charging:
            self._publish_stop()
            self.get_logger().warning(
                "MCU charging interlock active; stopping person tracking")
        else:
            self.get_logger().info(
                "MCU charging interlock cleared; centering head before tracking")

    def _control_tick(self) -> None:
        if self._charging:
            self._publish_stop()
            return
        if not self._head_ready:
            self._publish_stop()
            self._try_center_head()
            return

        command = self.controller.step(time.monotonic())
        if command.head_shift:
            shift = Int32()
            shift.data = command.head_shift
            self._head_shift_pub.publish(shift)
            self.controller.record_head_shift(command.head_shift)

        twist = Twist()
        twist.angular.z = command.angular_z
        self._cmd_vel_pub.publish(twist)

        if command.state != self._last_state:
            self.get_logger().info(
                f"tracker state: {self._last_state} -> {command.state}"
                + (f" (track {command.target_id})" if command.target_id else ""))
            self._last_state = command.state

        if command.center_head:
            self._head_ready = False
            self._try_center_head()

    def _try_center_head(self) -> None:
        if self._center_pending:
            return
        now = time.monotonic()
        if now - self._last_center_attempt < self._center_retry_interval:
            return
        if not self._head_center_client.service_is_ready():
            if now - self._last_center_attempt >= 5.0:
                self.get_logger().warning(
                    "waiting for head center service; base motion remains stopped")
                self._last_center_attempt = now
            return

        request = SetHeadAngle.Request()
        request.angle = 0
        self._center_pending = True
        self._last_center_attempt = now
        try:
            future = self._head_center_client.call_async(request)
        except Exception as exc:
            self._center_pending = False
            self.get_logger().error(f"head center service request failed: {exc}")
            return
        future.add_done_callback(self._on_head_centered)

    def _on_head_centered(self, future) -> None:
        self._center_pending = False
        try:
            response = future.result()
        except Exception as exc:
            self.get_logger().error(f"head center service call failed: {exc}")
            self._head_ready = False
            return
        if not response.success:
            self.get_logger().error(
                f"head center request rejected: {response.message}")
            self._head_ready = False
            return

        self.controller.reset_head_estimate()
        self._head_ready = True
        self.get_logger().info("head centered; person tracking enabled")

    def _publish_stop(self) -> None:
        self._cmd_vel_pub.publish(Twist())

    def destroy_node(self) -> None:
        self._publish_stop()
        super().destroy_node()


def main() -> None:
    rclpy.init()
    node = None
    try:
        node = PersonTrackerNode()
        rclpy.spin(node)
    except (KeyboardInterrupt, rclpy.executors.ExternalShutdownException):
        pass
    finally:
        if node is not None:
            node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()
