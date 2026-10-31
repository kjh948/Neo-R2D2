#!/usr/bin/env python3
"""Inspect IMU topic timing, frame, measurements, and empirical variance."""

import argparse
import math
import statistics
import time

import rclpy
from rclpy.node import Node
from sensor_msgs.msg import Imu


class ImuAnalyzer(Node):
    def __init__(self, topic):
        super().__init__("mpu6050_imu_analyzer")
        self.samples = []
        self.frames = set()
        self.header_stamps = []
        self.arrival_times = []
        self.message_covariances = []
        self.invalid_samples = 0
        self.subscription = self.create_subscription(Imu, topic, self._callback, 10)

    def _callback(self, message):
        values = (
            message.angular_velocity.x,
            message.angular_velocity.y,
            message.angular_velocity.z,
            message.linear_acceleration.x,
            message.linear_acceleration.y,
            message.linear_acceleration.z,
        )
        self.frames.add(message.header.frame_id)
        if not all(math.isfinite(value) for value in values):
            self.invalid_samples += 1
            return

        stamp = message.header.stamp.sec + message.header.stamp.nanosec * 1e-9
        self.header_stamps.append(stamp)
        self.arrival_times.append(time.monotonic())
        self.samples.append(values)
        self.message_covariances.append((
            list(message.angular_velocity_covariance),
            list(message.linear_acceleration_covariance),
            list(message.orientation_covariance),
        ))


def _axis_stats(samples, offset):
    axes = list(zip(*(sample[offset:offset + 3] for sample in samples)))
    result = []
    for values in axes:
        mean = statistics.mean(values)
        variance = statistics.variance(values) if len(values) > 1 else 0.0
        result.append((mean, math.sqrt(variance), variance))
    return result


def _print_covariance(label, covariance):
    print(f"{label}:")
    for row in range(3):
        print("  [" + ", ".join(f"{covariance[row * 3 + col]:.8g}"
                                for col in range(3)) + "]")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--topic", default="/imu/mpu6050")
    parser.add_argument("--duration", type=float, default=10.0,
                        help="collection duration in seconds (default: 10)")
    parser.add_argument("--expected-frame",
                        help="require every received message to use this frame_id")
    parser.add_argument("--min-rate", type=float, default=80.0,
                        help="warn if measured header rate is below this Hz (default: 80)")
    parser.add_argument("--stationary", action="store_true",
                        help="attest that the IMU stayed still; print candidate YAML variances")
    args, ros_args = parser.parse_known_args()
    if args.duration <= 0:
        parser.error("--duration must be greater than zero")

    rclpy.init(args=ros_args)
    node = ImuAnalyzer(args.topic)
    deadline = time.monotonic() + args.duration
    try:
        while rclpy.ok() and time.monotonic() < deadline:
            rclpy.spin_once(node, timeout_sec=min(0.1, deadline - time.monotonic()))
    except KeyboardInterrupt:
        print("\nInterrupted; reporting samples collected so far.")
    finally:
        node.destroy_node()
        rclpy.shutdown()

    count = len(node.samples)
    print(f"Topic: {args.topic}")
    print(f"Valid samples: {count}; invalid samples: {node.invalid_samples}")
    print(f"Observed frame_id(s): {', '.join(sorted(node.frames)) or '(none)'}")
    if count == 0:
        print("ERROR: no valid IMU samples received.")
        return 2

    frame_ok = (args.expected_frame is None or
                node.frames == {args.expected_frame})
    if args.expected_frame:
        print(f"Expected frame_id: {args.expected_frame} "
              f"({'OK' if frame_ok else 'MISMATCH'})")

    header_intervals = [
        later - earlier
        for earlier, later in zip(node.header_stamps, node.header_stamps[1:])
    ]
    monotonic_stamps = all(interval > 0 for interval in header_intervals)
    if len(node.header_stamps) > 1 and monotonic_stamps:
        header_rate = (count - 1) / (node.header_stamps[-1] - node.header_stamps[0])
        print(f"Header timestamp rate: {header_rate:.2f} Hz")
        print(f"Header timestamp interval: "
              f"mean={statistics.mean(header_intervals) * 1000:.2f} ms, "
              f"stdev={(statistics.pstdev(header_intervals) * 1000):.2f} ms")
        rate_ok = header_rate >= args.min_rate
        if not rate_ok:
            print(f"WARNING: below requested minimum rate ({args.min_rate:g} Hz).")
    else:
        rate_ok = False
        print("WARNING: timestamps are not strictly increasing or too few to estimate rate.")
    if len(node.arrival_times) > 1:
        arrival_intervals = [
            later - earlier
            for earlier, later in zip(node.arrival_times, node.arrival_times[1:])
        ]
        arrival_rate = (count - 1) / (node.arrival_times[-1] - node.arrival_times[0])
        print(f"Subscriber arrival rate: {arrival_rate:.2f} Hz; "
              f"interval stdev={statistics.pstdev(arrival_intervals) * 1000:.2f} ms")

    gyro = _axis_stats(node.samples, 0)
    accel = _axis_stats(node.samples, 3)
    print("Angular velocity (rad/s): axis: mean, sample_std, sample_variance")
    for axis, values in zip("xyz", gyro):
        print(f"  {axis}: {values[0]:.8g}, {values[1]:.8g}, {values[2]:.8g}")
    print("Linear acceleration (m/s^2): axis: mean, sample_std, sample_variance")
    for axis, values in zip("xyz", accel):
        print(f"  {axis}: {values[0]:.8g}, {values[1]:.8g}, {values[2]:.8g}")
    accel_norm = [
        math.sqrt(sum(value * value for value in sample[3:6]))
        for sample in node.samples
    ]
    print(f"Acceleration magnitude: mean={statistics.mean(accel_norm):.5g} m/s^2 "
          "(stationary near Earth: about 9.81 if gravity is not removed)")

    angular_cov, linear_cov, orientation_cov = node.message_covariances[-1]
    _print_covariance("Last message angular_velocity_covariance [(rad/s)^2]", angular_cov)
    _print_covariance("Last message linear_acceleration_covariance [(m/s^2)^2]", linear_cov)
    orientation_unknown = orientation_cov[0] == -1.0
    print("Orientation: unavailable marker is "
          f"{'present' if orientation_unknown else 'not present'} "
          f"(orientation_covariance[0]={orientation_cov[0]:g})")
    if all(value == 0.0 for value in angular_cov):
        print("WARNING: angular velocity covariance is all zero (unknown/unconfigured).")
    if all(value == 0.0 for value in linear_cov):
        print("WARNING: linear acceleration covariance is all zero (unknown/unconfigured).")

    if args.stationary:
        print("Candidate stationary diagonal variances (verify before EKF use):")
        for axis, value in zip("xyz", gyro):
            print(f"  gyro_{axis}_variance: {value[2]:.8g}")
        for axis, value in zip("xyz", accel):
            print(f"  accel_{axis}_variance: {value[2]:.8g}")
        print("These are observed sample variances, not a complete sensor-noise model; "
              "long-term bias and vibration are not captured.")
    else:
        print("No covariance parameter suggestions emitted. Repeat with --stationary "
              "only while the robot and sensor are physically still.")

    return 0 if frame_ok and rate_ok and monotonic_stamps else 1


if __name__ == "__main__":
    raise SystemExit(main())
