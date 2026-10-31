#!/usr/bin/env python3
"""Motion bench for the R2-D2 actuator chain (calibration verification).

Sequence: STRAIGHT <distance> m  ->  TURN <angle> deg in place  ->  STRAIGHT
<distance> m.  Drives /cmd_vel at fixed test speeds and judges each phase by rf2o's /odom
NET metrics (displacement from phase-start / unwrapped net yaw change) — the
arc integral sum(|dp|) is logged too, but only as a jitter gauge: it inflates
badly at walking speeds and must not drive the pass criterion. This validates
the twist_mapping calibration loop: command -> MCU execution -> laser odometry.

Prerequisites (Pi):
  * motor + lidar + rf2o running, NO nav2 controller fighting for /cmd_vel:
        ros2 launch r2d2_navigation navigation.launch.py enable_nav2:=false
  * robot at its (re)localization start, >= 2 m of CLEAR floor ahead (the run
    ends 2 m away in reverse), and >= 1 m around the rotation spot
  * tape marks every 0.5 m recommended: odom itself is only ±20% accurate at
    these speeds — the tape is the physical ground truth, odom the comparison

Safety: every phase is wall-clock capped (3x nominal time, hard stop), the
node publishes a zero Twist for 1 s after each phase (motor_node also
self-stops after cmd_vel_timeout 0.5 s), and Ctrl+C stops immediately.

Usage:
    ros2 run r2d2_motor motion_bench [--distance 1.0] [--angle-deg 180]
        [--speed 0.10] [--omega 0.30] [--csv /tmp/motion_bench.csv]

Exit codes: 0 all phases reached their targets within tolerance, 2 a phase
hit its timeout cap (robot stalled / estimate broke), 3 prerequisites
missing (no /odom, no /cmd_vel ack).
"""
import argparse
import csv
import math
import sys
import time

import rclpy
from geometry_msgs.msg import Twist
from nav_msgs.msg import Odometry
from rclpy.node import Node

PHASE_STRAIGHT = "straight"
PHASE_TURN = "turn"

CMD_RATE_HZ = 20.0
SETTLE_AFTER_STOP = 2.0        # pause between phases
DIST_TOLERANCE = 0.15          # m, phase pass criterion vs target
YAW_TOLERANCE = math.radians(20.0)


def yaw_from_quat(q) -> float:
    return math.atan2(2.0 * (q.w * q.z + q.x * q.y), 1.0 - 2.0 * (q.y * q.y + q.z * q.z))


class MotionBench(Node):
    def __init__(self):
        super().__init__("motion_bench")
        self.pub = self.create_publisher(Twist, "/cmd_vel", 10)
        self.last_odom = None
        self.prev_yaw = None
        # NET metrics (phase-start referenced): rf2o jitter must NOT accumulate
        # into the pass criterion — the first bench revision used sum(|dp|)
        # which inflated 0.1 m/s cmd to 0.165 odom and 0.3 rad/s to 60 deg/s.
        self.origin = None           # (x, y) at phase start
        self.yaw0_total = 0.0        # continuous yaw at phase start
        self.yaw_total = 0.0         # continuous (unwrapped) yaw now
        self.path_distance = 0.0     # secondary: arc integral (noise gauge)
        self.create_subscription(Odometry, "/odom", self._odom_cb, 10)

    def _odom_cb(self, msg: Odometry):
        p = msg.pose.pose
        yaw = yaw_from_quat(p.orientation)
        if self.last_odom is not None:
            self.path_distance += math.hypot(p.position.x - self.last_odom[0],
                                             p.position.y - self.last_odom[1])
            dyaw = math.atan2(math.sin(yaw - self.prev_yaw), math.cos(yaw - self.prev_yaw))
            self.yaw_total += dyaw
        self.last_odom = (p.position.x, p.position.y)
        self.prev_yaw = yaw

    # ---- helpers -----------------------------------------------------------
    def reset_phase(self):
        self.path_distance = 0.0
        # anchor nets to the CURRENT odom state (settle pause already flushed)
        if self.last_odom is not None:
            self.origin = self.last_odom
            self.yaw0_total = self.yaw_total

    @property
    def net_distance(self):
        if self.last_odom is None or self.origin is None:
            return 0.0
        return math.hypot(self.last_odom[0] - self.origin[0], self.last_odom[1] - self.origin[1])

    @property
    def net_yaw(self):
        return self.yaw_total - self.yaw0_total

    def send(self, v: float, w: float):
        t = Twist()
        t.linear.x, t.angular.z = v, w
        self.pub.publish(t)

    def wait_odom(self, timeout=10.0) -> bool:
        t0 = time.time()
        while rclpy.ok() and self.last_odom is None and time.time() - t0 < timeout:
            rclpy.spin_once(self, timeout_sec=0.1)
        return self.last_odom is not None

    def phase(self, name, v, w, target, tol, time_cap, log):
        """Drive one phase; returns (achieved, ok, elapsed)."""
        self.reset_phase()
        t0 = time.time()
        achieved = 0.0
        ok = False
        while rclpy.ok():
            rclpy.spin_once(self, timeout_sec=1.0 / CMD_RATE_HZ)
            self.send(v, w)
            achieved = self.net_distance if name == PHASE_STRAIGHT else abs(self.net_yaw)
            dt = time.time() - t0
            log.writerow([f"{dt:.2f}", name, f"{v:.3f}", f"{w:.3f}",
                          f"{self.net_distance:.3f}", f"{math.degrees(self.net_yaw):.1f}",
                          f"{self.path_distance:.3f}", f"{achieved >= target:.0f}"])
            if achieved + tol >= target:
                ok = True
                break
            if dt >= time_cap:
                break
        # hard stop + settle
        t_stop = time.time()
        while time.time() - t_stop < 1.0:
            self.send(0.0, 0.0)
            rclpy.spin_once(self, timeout_sec=0.05)
        time.sleep(SETTLE_AFTER_STOP)
        return achieved, ok, time.time() - t0


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--distance", type=float, default=1.0, help="straight length per leg [m]")
    ap.add_argument("--angle-deg", type=float, default=180.0, help="in-place turn [deg]")
    ap.add_argument("--speed", type=float, default=0.10, help="straight cmd [m/s]")
    ap.add_argument("--omega", type=float, default=0.30, help="turn cmd [rad/s]")
    ap.add_argument("--csv", default="/tmp/motion_bench.csv")
    args = ap.parse_args()

    rclpy.init()
    node = MotionBench()
    try:
        if not node.wait_odom():
            print("no /odom — start rf2o first (navigation.launch.py enable_nav2:=false)")
            return 3

        logf = open(args.csv, "w", newline="")
        log = csv.writer(logf)
        log.writerow(["t", "phase", "cmd_v", "cmd_w", "net_dist_m", "net_yaw_deg",
                      "path_integral_m", "hit_net"])

        angle = math.radians(args.angle_deg)
        results = []
        phases = [
            (PHASE_STRAIGHT, args.speed, 0.0, args.distance, DIST_TOLERANCE,
             3.0 * args.distance / args.speed, f"직진 {args.distance} m"),
            (PHASE_TURN, 0.0, args.omega, angle, YAW_TOLERANCE,
             3.0 * angle / args.omega, f"제자리 회전 {args.angle_deg:.0f}°"),
            (PHASE_STRAIGHT, args.speed, 0.0, args.distance, DIST_TOLERANCE,
             3.0 * args.distance / args.speed, f"직진 {args.distance} m (복귀)"),
        ]
        all_ok = True
        for name, v, w, target, tol, cap, label in phases:
            print(f"[bench] ▶ {label}   (cmd v={v} w={w}, cap {cap:.0f}s)")
            achieved, ok, dt = node.phase(name, v, w, target, tol, cap, log)
            got = achieved if name == PHASE_STRAIGHT else math.degrees(achieved)
            want = target if name == PHASE_STRAIGHT else args.angle_deg
            ratio = (achieved / target) if target else 1.0
            print(f"[bench] {'✅' if ok else '⏱ TIMEOUT'} {label}: 실측(odom) {got:.2f}"
                  f"{' m' if name == PHASE_STRAIGHT else '°'} / 목표 {want:.2f}"
                  f" — 도달비 {ratio:.0%}, 소요 {dt:.1f}s"
                  f" (cmd기준 기대속도 {v or args.omega:.2f}, odom속도 "
                  f"{(got / dt if dt else 0):.3f})")
            results.append((label, got, want, ratio, ok))
            all_ok = all_ok and ok

        print("\n=== 요약 (판단: odom 도달비 ≈ 90~110% 이면 cmd↔실행 캘리브레이션 정상;"
              " 물리 테이프와 odom이 ±20% 이내면 rf2o scale 정상) ===")
        for label, got, want, ratio, ok in results:
            print(f"  {'OK ' if ok else 'TIMEOUT'}  {label:28s} odom {got:7.2f} / 목표 {want:7.2f}  ({ratio:.0%})")
        print(f"  raw log: {args.csv}")
        logf.close()
        return 0 if all_ok else 2
    except KeyboardInterrupt:
        node.send(0.0, 0.0)
        print("\n[bench] interrupted — stopped.")
        return 2
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    sys.exit(main())
