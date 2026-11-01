#!/usr/bin/env python3
"""Motion bench for the R2-D2 actuator chain (calibration verification).

Two modes:

* `sequence` (default): STRAIGHT <distance> m  ->  TURN <angle> deg in place ->
  STRAIGHT <distance> m.  Drives /cmd_vel at fixed test speeds and judges each
  phase by rf2o's /odom NET metrics (displacement from phase-start / unwrapped
  net yaw change) — the arc integral sum(|dp|) is logged too, but only as a
  jitter gauge: it inflates badly at walking speeds and must not drive the
  pass criterion. This validates the twist_mapping calibration loop:
  command -> MCU execution -> laser odometry.

* `minvel`: find the SMALLEST /cmd_vel linear.x and angular.z that actually
  move the robot. Ascending open-loop sweeps (each candidate speed held for a
  fixed trial, then hard-stopped); the first trial whose NET odom motion
  exceeds a noise floor counts as "moved", and the boundary is then refined
  downward. Below the physical deadband the MCU simply does not move — that is
  exactly the stall region nav2 controllers must never command into. Only the
  moved trial ever travels (sub-threshold trials leave the robot in place), so
  the linear sweep needs ~0.5 m of clear floor, not a corridor. Ends with the
  nav2/motor parameter values derived from the found limits:
    - controller_server FollowPath: MPPI vx_min / RPP
      regulated_linear_scaling_min_speed must sit AT OR ABOVE the linear limit
    - r2d2_motor min_power: the power the mapping assigns to the smallest
      moving command — flooring it makes EVERY source (RPP, MPPI, teleop,
      recoveries) physically unable to issue a stalling non-zero move
    - behavior_server min_rotational_vel / RPP rotate_to_heading_angular_vel
      must exceed the rotation limit

Prerequisites (Pi):
  * motor (mapping_mode:=tank, as r2d2_navigation launches it) + lidar + rf2o
    running, NO nav2 controller fighting for /cmd_vel:
        ros2 launch r2d2_navigation navigation.launch.py enable_nav2:=false
  * robot at its (re)localization start, >= 2 m of CLEAR floor ahead (the run
    ends 2 m away in reverse), and >= 1 m around the rotation spot
    (minvel mode: >= 0.5 m ahead is enough)
  * tape marks every 0.5 m recommended: odom itself is only ±20% accurate at
    these speeds — the tape is the physical ground truth, odom the comparison

Safety: every phase is wall-clock capped (3x nominal time, hard stop), the
node publishes a zero Twist for 1 s after each phase (motor_node also
self-stops after cmd_vel_timeout 0.5 s), and Ctrl+C stops immediately.

Usage:
    ros2 run r2d2_motor motion_bench [--distance 1.0] [--angle-deg 180]
        [--speed 0.10] [--omega 0.30] [--csv /tmp/motion_bench.csv]
    ros2 run r2d2_motor motion_bench --mode minvel [--lin-step 0.005]
        [--ang-step 0.01] [--trial-time 2.5] [--move-tol 0.03]
        [--yaw-tol-deg 5.0] [--refine 0.002]

Exit codes: 0 all phases reached their targets within tolerance (sequence) or
both speed limits found (minvel), 2 a phase hit its timeout cap (robot
stalled / estimate broke) or a sweep never moved within its range, 3
prerequisites missing (no /odom, no /cmd_vel ack).
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
MINVEL_SETTLE = 1.5            # shorter pause between minvel sweep trials
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

    def trial(self, v, w, duration, log, label):
        """Hold one open-loop command for `duration` s, hard-stop, return
        (net_dist, net_yaw, elapsed). Used by the minvel sweeps: below the
        physical deadband the robot simply stays in place, so a trial either
        moves (net >= tolerance) or contributes ~nothing to the odom metrics."""
        self.reset_phase()
        t0 = time.time()
        while rclpy.ok() and time.time() - t0 < duration:
            rclpy.spin_once(self, timeout_sec=1.0 / CMD_RATE_HZ)
            self.send(v, w)
            log.writerow([f"{time.time() - t0:.2f}", label, f"{v:.3f}", f"{w:.3f}",
                          f"{self.net_distance:.3f}", f"{math.degrees(self.net_yaw):.1f}",
                          f"{self.path_distance:.3f}", "0"])
        dt = max(time.time() - t0, 1e-6)
        t_stop = time.time()
        while time.time() - t_stop < 1.0:
            self.send(0.0, 0.0)
            rclpy.spin_once(self, timeout_sec=0.05)
        time.sleep(MINVEL_SETTLE)
        return self.net_distance, self.net_yaw, dt

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


def run_minvel(node, args, log):
    """Measure the minimum executable linear.x and angular.z of the whole
    actuator chain (MCU deadband + leg stiction + mapping quantisation).

    Only trials that MOVE contribute travel, and the sweep stops at the first
    one that does — the linear sweep wanders < 0.3 m total around its start."""
    print("[minvel] 기준선: 무명령 정지 상태 odom 지터 측정")
    d0, yaw0, dt0 = node.trial(0.0, 0.0, args.trial_time, log, "baseline")
    print(f"[minvel]   정지 {args.trial_time:.1f} s 동안 odom 오차: {d0:.3f} m / "
          f"{math.degrees(yaw0):.1f}° (임계값 move-tol={args.move_tol} m, "
          f"yaw-tol={args.yaw_tol_deg}°)")
    if d0 > args.move_tol or abs(math.degrees(yaw0)) > args.yaw_tol_deg:
        print("[minvel] ⚠ 정지 상태인데 odom이 움직임 — rf2o/EKF 지터가 임계값보다 큼, "
              "임계값을 올리거나 바닥/라이다 환경을 확인")

    lin_cmd = lin_exec = ang_cmd = ang_exec = None
    if args.axis in ("linear", "both"):
        print(f"\n[minvel] ▶ linear.x 스윕 {args.lin_min:.3f}→{args.lin_max:.3f} "
              f"(step {args.lin_step:.3f}, {args.trial_time:.1f}s/trial)")
        lin_cmd, lin_exec = _sweep(
            node, args, log, "lin", args.lin_min, args.lin_max, args.lin_step,
            publish=lambda v: (v, 0.0), trial_time=args.trial_time,
            measure=lambda d, yaw, dt: d, threshold=args.move_tol,
            unit="m/s", fmt="{:.3f}")

    if args.axis in ("angular", "both"):
        print(f"\n[minvel] ▶ angular.z 스윕 {args.ang_min:.3f}→{args.ang_max:.3f} "
              f"(step {args.ang_step:.3f}, {args.rot_trial_time:.1f}s/trial)")
        # Longer trials: at the limit the robot turns a few deg/s, and a short
        # window cannot separate that from odom yaw jitter.
        ang_cmd, ang_exec = _sweep(
            node, args, log, "rot", args.ang_min, args.ang_max, args.ang_step,
            publish=lambda w: (0.0, w), trial_time=args.rot_trial_time,
            measure=lambda d, yaw, dt: abs(yaw), threshold=args.yaw_tol,
            unit="rad/s", fmt="{:.3f}")

    print("\n=== 최소 구동 속도 요약 ===")
    if lin_cmd is not None:
        print(f"  linear : cmd ≥ {lin_cmd:.3f} m/s 에서 구동 시작 "
              f"(그 commanded에서 실측 {lin_exec:.3f} m/s) — 그 이하 non-zero "
              f"cmd는 MCU deadband/stiction으로 정지")
    else:
        print(f"  linear : {args.lin_max:.3f} m/s까지 미구동 — 스윕 범위 확인")
    if ang_cmd is not None:
        print(f"  angular: cmd ≥ {ang_cmd:.3f} rad/s 에서 구동 시작 "
              f"(measured {ang_exec:.3f} rad/s)")
    else:
        print(f"  angular: {args.ang_max:.3f} rad/s까지 미구동 — 스윕 범위 확인")

    if lin_cmd is None and ang_cmd is None:
        return 2
    print_minvel_params(args, lin_cmd, lin_exec, ang_cmd, ang_exec)
    return 0


def _sweep(node, args, log, tag, lo, hi, step, publish, trial_time,
           measure, threshold, unit, fmt):
    """One ascending sweep + downward refinement. Returns (cmd_limit,
    measured rate at the limit) or (None, None)."""
    cmd, best_cmd, best_rate = lo, None, None
    while cmd <= hi + 1e-9:
        v, w = publish(cmd)
        d, yaw, dt = node.trial(v, w, trial_time, log, f"{tag}{cmd:.3f}")
        got = measure(d, yaw, dt)
        moved = got >= threshold
        rate = got / dt
        print(f"[minvel]   cmd {fmt.format(cmd)} {unit} → "
              f"{'움직임' if moved else '정지'} (net {rate:.3f} {unit})")
        if moved:
            best_cmd, best_rate = cmd, rate
            break
        cmd += step
    if best_cmd is None:
        return None, None
    if args.refine > 1e-9:
        # Walk back down in refine steps as long as the robot still moves:
        # the sweep step is coarser than the motor's power quantisation.
        fine = best_cmd - args.refine
        while fine > lo - 1e-9:
            v, w = publish(fine)
            d, yaw, dt = node.trial(v, w, trial_time, log, f"{tag}~{fine:.3f}")
            got = measure(d, yaw, dt)
            if got >= threshold:
                best_cmd, best_rate = fine, got / dt
                fine -= args.refine
            else:
                break
    return best_cmd, best_rate


def print_minvel_params(args, lin_cmd, lin_exec, ang_cmd, ang_exec):
    """Translate the measured limits into the parameter values they should
    appear in — see the module docstring."""
    # Tank-mapping power for a pure-axis command (must mirror twist_mapping):
    #   power = 100 * hypot(v, w*lever) / (forward_gain*100), lever=fwd/rot
    lever = args.forward_gain / max(args.rotation_gain, 1e-9)
    powers = []
    if lin_cmd is not None:
        powers.append(100.0 * lin_cmd / (args.forward_gain * 100.0))
    if ang_cmd is not None:
        powers.append(100.0 * ang_cmd * lever / (args.forward_gain * 100.0))
    min_power = math.ceil(max(powers)) if powers else 1

    print("\n=== nav2_params.yaml / motor.launch.py 반영 권장값 ===")
    if lin_cmd is not None:
        print(f"  MPPI  FollowPath.vx_min: {lin_cmd:.3f}   "
              f"(이하 cmd는 모터가 실행 불가 — 스톨 방지)")
        print(f"  RPP   FollowPath.regulated_linear_scaling_min_speed: "
              f"{max(lin_cmd, 0.05):.3f}   (curvature/cost 감속 하한; 최소 구동 "
              f"속도보다 낮으면 코너에서 스톨)")
    if ang_cmd is not None:
        print(f"  RPP   FollowPath.rotate_to_heading_angular_vel ≥ {ang_cmd:.3f}"
              f"   (현재 0.4 — 하한 대비 여유 확인)")
        print(f"  behaviors  min_rotational_vel ≥ {ang_cmd:.3f} (현재 0.1)")
    print(f"  motor.launch.py  min_power:={min_power}   "
          f"(non-zero move의 power 하한 — RPP/MPPI/teleop/recovery 전부 "
          f"STALL 구간 cmd가 모터에 도착하지 못하게 하는 최종 안전망)")
    print(f"  velocity_smoother min_velocity 하한 기능 없음 — 변경 불필요 "
          f"(위 min_power가 담당)")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--mode", choices=["sequence", "minvel"], default="sequence",
                    help="sequence = calibration walk (default); "
                         "minvel = sweep the minimum executable linear/angular speed")
    ap.add_argument("--axis", choices=["linear", "angular", "both"], default="both",
                    help="minvel mode: which axis to sweep")
    ap.add_argument("--lin-min", dest="lin_min", type=float, default=0.005)
    ap.add_argument("--lin-max", dest="lin_max", type=float, default=0.08)
    ap.add_argument("--lin-step", dest="lin_step", type=float, default=0.005)
    ap.add_argument("--ang-min", dest="ang_min", type=float, default=0.01)
    ap.add_argument("--ang-max", dest="ang_max", type=float, default=0.10)
    ap.add_argument("--ang-step", dest="ang_step", type=float, default=0.01)
    ap.add_argument("--trial-time", type=float, default=2.5,
                    help="minvel: hold time per linear trial [s]")
    ap.add_argument("--rot-trial-time", type=float, default=8.0,
                    help="minvel: hold time per rotation trial [s] (long — "
                         "must resolve a few deg/s from odom yaw jitter)")
    ap.add_argument("--move-tol", type=float, default=0.03,
                    help="minvel: net displacement counted as real motion [m]")
    ap.add_argument("--yaw-tol-deg", dest="yaw_tol_deg", type=float, default=4.0,
                    help="minvel: net yaw counted as real rotation [deg] "
                         "(detection floor = yaw_tol/rot_trial_time rad/s — "
                         "keep it BELOW the smallest cmd you want resolved)")
    ap.add_argument("--refine", type=float, default=0.002,
                    help="minvel: downward refinement step (0 disables)")
    # Must match the RUNNING motor node's calibration (defaults = motor.launch.py)
    ap.add_argument("--forward-gain", type=float, default=0.001525)
    ap.add_argument("--rotation-gain", type=float, default=0.00797)
    ap.add_argument("--distance", type=float, default=1.0, help="straight length per leg [m]")
    ap.add_argument("--angle-deg", type=float, default=180.0, help="in-place turn [deg]")
    ap.add_argument("--speed", type=float, default=0.10, help="straight cmd [m/s]")
    ap.add_argument("--omega", type=float, default=0.30, help="turn cmd [rad/s]")
    ap.add_argument("--csv", default="/tmp/motion_bench.csv")
    args = ap.parse_args()
    args.yaw_tol = math.radians(args.yaw_tol_deg)

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

        if args.mode == "minvel":
            rc = run_minvel(node, args, log)
            print(f"  raw log: {args.csv}")
            logf.close()
            return rc

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
