"""Twist -> MCU ``move`` mapping, kept free of ROS imports so it can be unit tested.

The MCU understands one locomotion frame::

    {"cmd": "move", "power": 0..100, "angle": deg}

where ``angle`` follows the MCU convention documented in ``r2d2/web/index.html``:
``0`` forward, ``180`` reverse, ``+90``/``-90`` lateral strafe. The original app
quantises its joystick to those four directions, so we map a differential-drive
style ``Twist`` (only ``linear.x`` + ``angular.z``, e.g. from ``teleop_twist_keyboard``)
onto the same four directions:

* dominant ``linear.x``  -> forward (0) or reverse (180)
* dominant ``angular.z`` -> strafe (+90 / -90), R2-D2 cannot spin in place here

``power`` is the dominant component normalised against its configured maximum,
linearly scaled to 1..100. A deadband keeps jitter around zero from creeping.
"""
from __future__ import annotations

from dataclasses import dataclass

# Strafe directions as understood by the MCU firmware.
ANGLE_FORWARD = 0
ANGLE_LEFT = 90
ANGLE_RIGHT = -90
ANGLE_REVERSE = 180


@dataclass(frozen=True)
class MoveMapping:
    max_linear_velocity: float = 0.5   # m/s -> power 100
    max_angular_velocity: float = 1.0  # rad/s -> power 100
    deadband: float = 0.02             # |normalised twist| below this counts as zero
    min_power: int = 1                 # lowest non-zero power to send (tunable)
    invert_strafe: bool = False        # flip if +angular.z strafes the wrong way


def twist_to_move(linear_x: float, angular_z: float, mapping: MoveMapping) -> tuple:
    """Return ``(power, angle)`` for the MCU ``move`` frame."""
    norm_lin = _clamp(linear_x / max(mapping.max_linear_velocity, 1e-6))
    norm_ang = _clamp(angular_z / max(mapping.max_angular_velocity, 1e-6))

    if max(abs(norm_lin), abs(norm_ang)) <= mapping.deadband:
        return 0, ANGLE_FORWARD

    if abs(norm_lin) >= abs(norm_ang):
        power = abs(norm_lin)
        angle = ANGLE_FORWARD if norm_lin > 0 else ANGLE_REVERSE
    else:
        power = abs(norm_ang)
        positive_left = not mapping.invert_strafe
        if angular_z > 0:
            angle = ANGLE_LEFT if positive_left else ANGLE_RIGHT
        else:
            angle = ANGLE_RIGHT if positive_left else ANGLE_LEFT

    return _scale_power(power, mapping.min_power), angle


def _clamp(value: float) -> float:
    return max(-1.0, min(1.0, value))


def _scale_power(normalised: float, min_power: int) -> int:
    power = round(normalised * 100.0)
    if power <= 0:
        return 0
    return max(int(min_power), min(100, power))
