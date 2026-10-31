#!/usr/bin/env python3
"""Live /odom monitor for the Neo-R2D2 fused RF2O/IMU bringup.

Run (after `source install/setup.bash`, bringup or navigation up):
    python3 src/r2d2_bringup/scripts/check_odom.py            # odom only
    python3 src/r2d2_bringup/scripts/check_odom.py --scan     # + wall sectors

Every second prints:
    x,y [m]  yaw [deg]   vx,wz [m/s]  (publish Hz)  | front rear left right [m]

Interpretation (upside-down COIN-D3, laser_tf_roll:=pi, lidar_reverse:=false):
  - push/drive the robot FORWARD  -> x must INCREASE, front sector DECREASE
  - turn LEFT (wz > 0)            -> yaw must INCREASE (CCW)
  - standing still on the dock    -> x/y/yaw must HOLD (rf2o idle deadband);
    continuous slow slide = gate too small, report the numbers
"""
import argparse
import math
import time

import rclpy
import rclpy.executors
from rclpy.node import Node
from rclpy.qos import QoSProfile, ReliabilityPolicy
from nav_msgs.msg import Odometry
from sensor_msgs.msg import LaserScan


def yaw_of(q):
    return math.atan2(2.0 * (q.w * q.z + q.x * q.y),
                      1.0 - 2.0 * (q.y * q.y + q.z * q.z))


class CheckOdom(Node):
    def __init__(self, with_scan):
        super().__init__("check_odom")
        self.with_scan = with_scan
        self.odom = None
        self.vel = (0.0, 0.0)
        self.n_odom = 0
        self.sectors = {k: float("nan") for k in ("front", "rear", "left", "right")}
        self.create_subscription(Odometry, "/odom", self._odom_cb, 10)
        if with_scan:
            be = QoSProfile(depth=5, reliability=ReliabilityPolicy.BEST_EFFORT)
            self.create_subscription(LaserScan, "/scan", self._scan_cb, be)

    def _odom_cb(self, m):
        self.odom = m
        self.n_odom += 1
        self.vel = (m.twist.twist.linear.x, m.twist.twist.angular.z)

    def _sector(self, m, center_deg, half=40.0):
        vals = []
        for i, r in enumerate(m.ranges):
            ang = math.degrees(m.angle_min + i * m.angle_increment)
            ang = (ang + 180.0) % 360.0 - 180.0
            if abs((ang - center_deg + 180.0) % 360.0 - 180.0) <= half:
                if math.isfinite(r) and m.range_min < r < m.range_max:
                    vals.append(r)
        return sum(vals) / len(vals) if vals else float("nan")

    def _scan_cb(self, m):
        self.sectors["front"] = self._sector(m, 0)
        self.sectors["rear"] = self._sector(m, 180)
        self.sectors["left"] = self._sector(m, 90)
        self.sectors["right"] = self._sector(m, -90)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--scan", action="store_true", help="also show wall-sector distances")
    ap.add_argument("--hz", action="store_true", help="print publish rate instead of pose")
    args = ap.parse_args()

    rclpy.init()
    node = CheckOdom(with_scan=args.scan)
    try:
        hz_window, hz_count, t_last = 5.0, [0], time.time()
        while True:
            t0 = time.time()
            while time.time() - t0 < (hz_window if args.hz else 1.0):
                rclpy.spin_once(node, timeout_sec=0.1)
            if args.hz:
                dt = time.time() - t_last
                print(f"/odom publish rate: {hz_count[0] / dt:.2f} Hz", flush=True)
                hz_count[0] = 0
                t_last = time.time()
                continue
            if node.odom is None:
                print("waiting for /odom ... (bringup running? lidar warmed up?)", flush=True)
                continue
            p = node.odom.pose.pose.position
            hz = (node.n_odom, )
            line = (f"x={p.x:+.3f} y={p.y:+.3f} yaw={math.degrees(yaw_of(node.odom.pose.pose.orientation)):7.2f}deg "
                    f"vx={node.vel[0]:+.3f} wz={node.vel[1]:+.3f}")
            if args.scan:
                s = node.sectors
                line += (f" | front={s['front']:5.2f} rear={s['rear']:5.2f}"
                         f" left={s['left']:5.2f} right={s['right']:5.2f}")
            print(line, flush=True)
    except (KeyboardInterrupt, rclpy.executors.ExternalShutdownException):
        pass
    finally:
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


if __name__ == "__main__":
    main()
