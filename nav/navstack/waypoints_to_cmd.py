"""Waypoint chunk -> R2D2 (power, angle) motion command. Pure functions.

Waypoint rows (navserve protocol): ``[forward_m, lateral_m, yaw_rad]`` at 0.1 s
spacing, +lateral = LEFT, +yaw = CCW, in the frame of the robot at capture time.

R2D2 ``move`` semantics (r2d2/web/index.html, info/protocol/UART_COMMANDS.md):
``angle`` = direction of travel in degrees, 0 = forward, 180 = reverse,
+/-90 = lateral strafe (the nanopis is omnidirectional); ``power`` 0-100 =
speed magnitude, open loop (no velocity setpoint). Command is a deadman:
repeat at >= 3 Hz or the robot just keeps walking.

Since R2D2 exposes direction+power only (no independent yaw), we track the
TRANSLATION vector of the path at the control period and fold path heading into
the direction choice; ``yaw_weight`` nudges the heading toward the path's local
yaw, and ``angle_sign`` absorbs the +left/+right convention question on real
hardware (default +1 = left-positive; flip if the robot circles the wrong way).
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

WP_DT_S = 0.1  # waypoint spacing (navserve default: 10 rows = 1 s)


@dataclass
class MotionParams:
    v_full_mps: float = 0.30   # robot speed at power 100 (calibrate on hardware)
    max_power: float = 60.0    # software speed cap
    min_power: float = 12.0    # below this the wheels barely overcome friction
    stop_dist_m: float = 0.03  # ignore sub-cm reference points
    angle_deadband_deg: float = 6.0
    angle_sign: float = 1.0    # +1 if angle>0 strafes LEFT on the robot
    yaw_weight: float = 0.3    # blend of path-local yaw into travel direction
    control_dt_s: float = 0.333


def sample_waypoints(waypoints: np.ndarray, t_s: float, dt_s: float = WP_DT_S) -> tuple[float, float, float]:
    """Interpolate (forward, lateral, yaw) at time t_s into the chunk.

    Row i is the pose at t=(i+1)*dt -- the chunk-start identity is NOT stored
    in the rows, so we anchor at the origin (0,0,0) at t=0. Beyond the chunk
    end, clamps to the last row (the robot converges to the path end).
    """
    wp = np.asarray(waypoints, dtype=np.float64)
    if wp.ndim != 2 or wp.shape[1] != 3 or len(wp) == 0:
        raise ValueError(f"waypoints must be (H,3), got {wp.shape}")
    pos = min(max(t_s / dt_s, 0.0), len(wp))  # 0=origin, k=row k-1
    i1 = min(int(math.ceil(pos)) - 1, len(wp) - 1)
    i0 = i1 - 1
    frac = pos - 1 - i0 if i0 >= 0 else pos
    a = wp[i0] if i0 >= 0 else np.zeros(3)
    b = wp[i1]
    fwd = a[0] + (b[0] - a[0]) * frac
    lat = a[1] + (b[1] - a[1]) * frac
    yaw = a[2] + (b[2] - a[2]) * frac  # tiny-angle lerp; chunks are <= ~1 rad total
    return fwd, lat, float(yaw)


def waypoint_to_motion(
    waypoints: np.ndarray, params: MotionParams, t_ref: float | None = None
) -> tuple[int, int]:
    """One control period: chunk -> (power, angle_deg) for the r2d2 ``move`` cmd.

    Returns (0, 0) for stop / degenerate chunks / stop-flagged inputs.
    """
    t = params.control_dt_s if t_ref is None else t_ref
    fwd, lat, yaw = sample_waypoints(np.asarray(waypoints), t)
    dist = math.hypot(fwd, lat)
    if dist < params.stop_dist_m:
        return 0, 0
    # direction of travel: path tangent, nudged by the local path yaw
    bearing = math.degrees(math.atan2(lat, fwd))
    bearing += math.degrees(yaw) * params.yaw_weight
    bearing = (bearing + 180.0) % 360.0 - 180.0
    angle = params.angle_sign * bearing
    if abs(angle) < params.angle_deadband_deg:
        angle = 0.0
    angle = max(-180.0, min(180.0, angle))
    if angle == -180.0:
        angle = 180.0  # r2d2 convention: reverse is +180
    angle = int(round(angle))
    v_cmd = dist / t
    power = v_cmd / params.v_full_mps * 100.0
    power = max(params.min_power, min(params.max_power, power))
    return int(round(power)), angle
