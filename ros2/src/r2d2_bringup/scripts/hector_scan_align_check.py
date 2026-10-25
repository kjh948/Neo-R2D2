#!/usr/bin/env python3
"""Offline check for the hector upside-down-mount scan/mirror bug.

Drives hector_mapping with SYNTHETIC scans (no hardware): a rectangular room
plus one asymmetric pillar, robot scripted through straight + yaw segments at
10 Hz. Scans follow THIS robot's data convention: bins carry the corrected
(= physical base-link) bearing angles (the COIN driver's angle/range pairing
cancels the upside-down mirror), published in frame `base_laser` while a
static base_link->base_laser TF with roll=pi, x=0.18 mimics the launcher.

After the drive it computes the FRACTION of the final scan's points (mapped
into the map frame through the same TF chain RViz uses) that land on an
occupied /map cell. A mirror-consistent mapping pipeline keeps this high even
after yaws; the bug (hector ignoring base<-laser TF when building its scan)
makes the stamped map mirror the drawn scan -> fraction collapses after turns.

    ros2 launch r2d2_bringup hector.launch.py enable_motor:=false enable_lidar:=false
    python3 src/r2d2_bringup/scripts/hector_scan_align_check.py

Exit code 0 if >= 0.85 of scan points hit walls, 1 otherwise.
"""
import math
import sys
import time

import rclpy
from rclpy.node import Node
from rclpy.qos import DurabilityPolicy, QoSProfile
from nav_msgs.msg import OccupancyGrid
from sensor_msgs.msg import LaserScan
from std_msgs.msg import String
from tf2_msgs.msg import TFMessage
from geometry_msgs.msg import Quaternion, TransformStamped

ROOM = [(-6.0, -8.0, 6.0, -8.0), (6.0, -8.0, 6.0, 8.0), (6.0, 8.0, -6.0, 8.0), (-6.0, 8.0, -6.0, -8.0)]
PILLAR = [(-1.0, -1.5, -0.5, -1.5), (-0.5, -1.5, -0.5, -2.0), (-0.5, -2.0, -1.0, -2.0), (-1.0, -2.0, -1.0, -1.5)]
BENCH = [(1.5, 1.0, 4.0, 1.0), (4.0, 1.0, 4.0, 3.5)]   # asymmetric L on the NE side
SEGMENTS = ROOM + PILLAR + BENCH

LASER_X = 0.18   # base_link -> base_laser offsets (mirror launcher defaults)
LASER_Z = 0.083
ROLL = 0.0       # launcher default (0 = keep the map left-right true)
YAW = 0.0        # laser_tf_yaw knob (180-deg encoder offset compensation)

N_BINS = 360
RATE = 10.0      # Hz


def cast_ray(px, py, ang):
    """Nearest segment hit distance from (px,py) along `ang` (base/physical frame)."""
    dx, dy = math.cos(ang), math.sin(ang)
    best = float("inf")
    for (ax, ay, bx, by) in SEGMENTS:
        ex, ey = bx - ax, by - ay
        den = dx * ey - dy * ex
        if abs(den) < 1e-12:
            continue
        t = ((ax - px) * ey - (ay - py) * ex) / den
        u = ((ax - px) * dy - (ay - py) * dx) / den
        if t > 0.0 and -1e-9 <= u <= 1.0 + 1e-9:
            best = min(best, t)
    return best if best < 30.0 else float("nan")


class Check(Node):
    def __init__(self):
        super().__init__("hector_scan_align_check")
        self.scan_pub = self.create_publisher(LaserScan, "scan", 10)
        # static base_link->base_laser TF (roll=pi), like the launcher's
        # static_transform_publisher (transient_local on /tf_static)
        static_qos = QoSProfile(depth=1, durability=DurabilityPolicy.TRANSIENT_LOCAL)
        self.tf_pub = self.create_publisher(TFMessage, "/tf_static", static_qos)
        self.map_msg = None
        self.tf_map_base = {}
        self.last_scan = None
        self.create_subscription(OccupancyGrid, "map", lambda m: setattr(self, "map_msg", m), 10)
        self.create_subscription(TFMessage, "/tf", self.on_tf, 50)
        self.publish_static_tf()

    def publish_static_tf(self):
        t = TransformStamped()
        t.header.frame_id = "base_link"
        t.child_frame_id = "base_laser"
        t.transform.translation.x = LASER_X
        t.transform.translation.z = LASER_Z
        # quaternion = Rz(yaw) * Rx(roll), matching static_transform_publisher
        hr, hy = ROLL / 2.0, YAW / 2.0
        sx, cx = math.sin(hr), math.cos(hr)
        sz, cz = math.sin(hy), math.cos(hy)
        t.transform.rotation = Quaternion(
            x=cz * sx, y=sz * sx, z=sz * cx, w=cz * cx - sz * sx)
        m = TFMessage()
        m.transforms = [t]
        self.tf_pub.publish(m)

    def on_tf(self, msg):
        for t in msg.transforms:
            if t.header.frame_id == "map" and t.child_frame_id == "base_link":
                self.tf_map_base[t.header.stamp.sec + t.header.stamp.nanosec * 1e-9] = t

    def emit_scan(self, rx, ry, yaw):
        s = LaserScan()
        now = self.get_clock().now().to_msg()
        s.header.stamp = now
        s.header.frame_id = "base_laser"
        s.angle_min = 0.0
        s.angle_max = 2 * math.pi
        s.angle_increment = 2 * math.pi / N_BINS
        s.range_min = 0.05
        s.range_max = 30.0
        sx, sy = rx + LASER_X * math.cos(yaw), ry + LASER_X * math.sin(yaw)
        ranges = []
        mirror = globals().get("SCAN_MIRROR", False)
        for i in range(N_BINS):
            bearing = yaw - i * s.angle_increment if mirror else yaw + i * s.angle_increment
            ranges.append(cast_ray(sx, sy, bearing))
        s.ranges = ranges
        self.last_scan = s
        self.scan_pub.publish(s)
        # keep the static TF latched fresh too (belt-and-braces for late subscribers)
        self.publish_static_tf()

    def align_fraction(self):
        if self.map_msg is None or not self.tf_map_base or self.last_scan is None:
            return None
        # newest map->base_link pose
        t = self.tf_map_base[max(self.tf_map_base)]
        q = t.transform.rotation
        # yaw of quaternion (z-up rotation only expected)
        yaw_e = math.atan2(2.0 * (q.w * q.z + q.x * q.y), 1.0 - 2.0 * (q.y * q.y + q.z * q.z))
        ex, ey = t.transform.translation.x, t.transform.translation.y
        s = self.last_scan
        occupied = self.map_msg.data
        w, h = self.map_msg.info.width, self.map_msg.info.height
        res, ox, oy = self.map_msg.info.resolution, self.map_msg.info.origin.position.x, self.map_msg.info.origin.position.y
        hits = total = 0
        for i, r in enumerate(s.ranges):
            if not math.isfinite(r):
                continue
            ang = s.angle_min + i * s.angle_increment
            # laser-frame point, then the SAME static TF RViz/hector use:
            # p_base = Rz(yaw)·Rx(roll)·p_laser + t
            lx, ly = r * math.cos(ang), r * math.sin(ang)
            if abs(ROLL) > 1e-6:
                ly = -ly
            cy, sy = math.cos(YAW), math.sin(YAW)
            bx = cy * lx - sy * ly + LASER_X
            by = sy * lx + cy * ly
            # map frame via hector's estimated pose
            mx = ex + bx * math.cos(yaw_e) - by * math.sin(yaw_e)
            my = ey + bx * math.sin(yaw_e) + by * math.cos(yaw_e)
            if not (math.isfinite(mx) and math.isfinite(my)):
                continue
            gx, gy = int((mx - ox) / res), int((my - oy) / res)
            total += 1
            ok = False
            for dyx in (-1, 0, 1):
                for dyy in (-1, 0, 1):
                    X, Y = gx + dyx, gy + dyy
                    if 0 <= X < w and 0 <= Y < h and occupied[Y * w + X] >= 50:
                        ok = True
            hits += ok
        return hits / total if total else None


def main():
    global LASER_X, LASER_Z, ROLL, YAW
    rclpy.init()
    node = Check()

    # scripted drive: [secs, vx (m/s in base frame), wz (rad/s)]
    # straight FIRST (an empty-map scan matcher needs walls before yawing),
    # and the path STAYS INSIDE the room (going out-of-bounds feeds all-MAX
    # range scans that legitimately drive any scan matcher crazy).
    # full-circle spins (45 deg/s at 10 Hz = 4.5 deg/scan) in place: heading
    # returns to 0 after each, so the robot stays inside the 6x8 m room.
    # Gentle drive (real R2-D2 walker): SLOW circle at 10 deg/s (1 deg/scan),
    # then one FAST 45 deg/s circle to see where pure scan matching breaks.
    # Path stays inside the 12x16 m room.
    plan = [
        (2.0, 0.0, 0.0),
        (4.0, 0.4, 0.0),                      # east 1.6 m -> walls imaged
        (36.0, 0.0, math.radians(10)),        # SLOW spin LEFT one full circle
        (3.0, 0.4, 0.0),                      # east 1.2 m
        (8.0, 0.0, -math.radians(45)),        # FAST spin RIGHT one circle
        (6.0, -0.3, 0.0),                     # back up 1.8 m
        (3.0, 0.0, math.radians(45)),         # quarter turn left
        (4.0, 0.4, 0.0),                      # forward 1.6 m
        (2.0, 0.0, 0.0),
    ]
    rx = ry = yaw = 0.0
    dt = 1.0 / RATE
    node.emit_scan(rx, ry, yaw)
    log_t = time.time()
    def latest_est():
        if not node.tf_map_base:
            return None
        ts = max(node.tf_map_base)
        t = node.tf_map_base[ts]
        q = t.transform.rotation
        ye = math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.y * q.y + q.z * q.z))
        lag = time.time() - ts
        return (t.transform.translation.x, t.transform.translation.y,
                math.degrees(ye), lag)
    for seg_i, (secs, vx, wz) in enumerate(plan):
        t_end = time.time() + secs
        while time.time() < t_end:
            # integrate in REAL elapsed time per iteration, not fixed dt:
            step = time.time() - log_t
            log_t = time.time()
            step = min(max(step, 0.01), 0.5)
            rx += vx * step * math.cos(yaw)
            ry += vx * step * math.sin(yaw)
            yaw += wz * step
            node.emit_scan(rx, ry, yaw)
            for _ in range(10):
                rclpy.spin_once(node, timeout_sec=0.0)
            time.sleep(dt)
        est = latest_est()
        print(f"seg{seg_i:>2} vx={vx:+.1f} wz={math.degrees(wz):+3.0f}d/s | "
              f"true: x={rx:+5.2f} y={ry:+5.2f} yaw={math.degrees(yaw):+6.1f} | "
              f"est: " + (f"x={est[0]:+5.2f} y={est[1]:+5.2f} yaw={est[2]:+6.1f} lag={est[3]:4.2f}s" if est else "n/a"))

    time.sleep(2.5)                      # let the final /map + TF land
    while time.time() < log_t + 2.5:
        rclpy.spin_once(node, timeout_sec=0.1)
    frac = node.align_fraction()
    est = node.tf_map_base[max(node.tf_map_base)] if node.tf_map_base else None
    print(f"true end pose:        x={rx:+.2f} y={ry:+.2f} yaw={math.degrees(yaw):+6.1f} deg")
    if est is not None:
        q = est.transform.rotation
        ye = math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.y * q.y + q.z * q.z))
        print(f"hector map->base_link: x={est.transform.translation.x:+.2f} "
              f"y={est.transform.translation.y:+.2f} yaw={math.degrees(ye):+6.1f} deg")
        print("(EXPECT: roll=0 -> est == true; roll=pi -> est mirrors true: y->-y, yaw->-yaw;"
              " yaw=pi -> heading stays physical, position ~ rotated 180 about start)")
    if frac is None:
        print("NO /map or no map->base_link TF received -> hector not running?")
        sys.exit(2)
    print(f"scan-on-map alignment: {frac * 100:.1f} % of final scan points on walls")
    sys.exit(0 if frac >= 0.85 else 1)


if __name__ == "__main__":
    # optional env overrides for A/B runs:
    #   HECTOR_TF_ROLL=0  HECTOR_TF_X=0  -> scan already in base frame
    #   HECTOR_SCAN_MIRROR=1 -> bins = raw upside-down sensor angles
    #       (physical base bearing = -bin; pair with HECTOR_TF_ROLL=pi)
    import os
    ROLL = float(os.environ.get("HECTOR_TF_ROLL", ROLL))
    LASER_X = float(os.environ.get("HECTOR_TF_X", LASER_X))
    YAW = float(os.environ.get("HECTOR_TF_YAW", YAW))
    SCAN_MIRROR = os.environ.get("HECTOR_SCAN_MIRROR", "0") == "1"
    try:
        main()
    except KeyboardInterrupt:
        pass
