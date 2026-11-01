"""ROS-independent person selection and head/base tracking controller."""
from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Iterable, Optional


@dataclass(frozen=True)
class PersonDetection:
    track_id: str
    center_x: float
    area: float
    score: float


@dataclass(frozen=True)
class TrackerCommand:
    head_shift: int = 0
    angular_z: float = 0.0
    center_head: bool = False
    target_id: Optional[str] = None
    state: str = "lost"


class PersonTrackerController:
    """Keep a person track, follow with the head, and turn the base at head limits."""

    def __init__(
        self,
        image_width: int = 640,
        camera_hfov_deg: float = 80.0,
        head_deadband_deg: float = 10.0,
        head_follow_gain: float = 0.5,
        max_head_shift_deg: int = 5,
        head_limit_deg: float = 30.0,
        head_recenter_deg: float = 10.0,
        base_rotation_speed: float = 0.105,
        # _head_angle > 0 means the person went out on the RIGHT of the frame
        # (head follows: positive shift = right, hardware-confirmed). The motor
        # honours the ROS twist convention (+wz = CCW/left), so turning toward
        # a right-side person needs NEGATIVE wz -> sign is -1.
        base_rotation_sign: int = -1,
        target_lost_timeout: float = 0.7,
    ) -> None:
        if image_width <= 0:
            raise ValueError("image_width must be positive")
        if not math.isfinite(camera_hfov_deg) or camera_hfov_deg <= 0.0:
            raise ValueError("camera_hfov_deg must be positive")
        if (not math.isfinite(head_deadband_deg)
                or not 0.0 <= head_deadband_deg < camera_hfov_deg / 2.0):
            raise ValueError("head_deadband_deg must be in [0, camera_hfov_deg/2)")
        if not math.isfinite(head_follow_gain) or head_follow_gain <= 0.0:
            raise ValueError("head_follow_gain must be positive")
        if max_head_shift_deg <= 0:
            raise ValueError("max_head_shift_deg must be positive")
        if not math.isfinite(head_limit_deg) or head_limit_deg <= 0.0:
            raise ValueError("head_limit_deg must be positive")
        if (not math.isfinite(head_recenter_deg)
                or not 0.0 <= head_recenter_deg < head_limit_deg):
            raise ValueError("head_recenter_deg must be in [0, head_limit_deg)")
        if not math.isfinite(base_rotation_speed) or base_rotation_speed <= 0.0:
            raise ValueError("base_rotation_speed must be positive")
        if base_rotation_sign not in (-1, 1):
            raise ValueError("base_rotation_sign must be -1 or 1")
        if not math.isfinite(target_lost_timeout) or target_lost_timeout <= 0.0:
            raise ValueError("target_lost_timeout must be positive")

        self.image_width = int(image_width)
        self.camera_hfov_deg = float(camera_hfov_deg)
        self.head_deadband_deg = float(head_deadband_deg)
        self.head_follow_gain = float(head_follow_gain)
        self.max_head_shift_deg = int(max_head_shift_deg)
        self.head_limit_deg = float(head_limit_deg)
        self.head_recenter_deg = float(head_recenter_deg)
        self.base_rotation_speed = float(base_rotation_speed)  # 0.15 minus 30% bench ask
        self.base_rotation_sign = int(base_rotation_sign)
        self.target_lost_timeout = float(target_lost_timeout)

        self.target_id: Optional[str] = None
        self._target: Optional[PersonDetection] = None
        self._last_seen: Optional[float] = None
        self._head_angle = 0.0
        self._state = "lost"
        self._center_requested = False

    @property
    def head_angle_estimate(self) -> float:
        return self._head_angle

    @property
    def state(self) -> str:
        return self._state

    def update(self, detections: Iterable[PersonDetection], now: float) -> None:
        people = [
            d for d in detections
            if d.track_id and math.isfinite(d.center_x)
            and math.isfinite(d.area) and d.area > 0.0
            and math.isfinite(d.score)
        ]

        if self.target_id is not None:
            target = next((d for d in people if d.track_id == self.target_id), None)
            if target is not None:
                self._target = target
                self._last_seen = now
                return
            if self._last_seen is not None and now - self._last_seen < self.target_lost_timeout:
                return

        self.target_id = None
        self._target = None
        if people:
            target = max(people, key=lambda d: (d.area, d.score))
            self.target_id = target.track_id
            self._target = target
            self._last_seen = now
            self._center_requested = False

    def record_head_shift(self, shift_deg: int) -> None:
        self._head_angle = max(
            -self.head_limit_deg,
            min(self.head_limit_deg, self._head_angle + int(shift_deg)),
        )

    def reset_head_estimate(self) -> None:
        self._head_angle = 0.0

    def step(self, now: float) -> TrackerCommand:
        fresh = (
            self._target is not None
            and self._last_seen is not None
            and now - self._last_seen <= self.target_lost_timeout
        )
        if not fresh:
            was_tracking = self._state != "lost"
            self._state = "lost"
            self._target = None
            self.target_id = None
            self._last_seen = None
            center_head = was_tracking and not self._center_requested
            self._center_requested = self._center_requested or center_head
            return TrackerCommand(center_head=center_head, state=self._state)

        self._center_requested = False
        if abs(self._head_angle) >= self.head_limit_deg:
            self._state = "align"
        elif self._state == "align" and abs(self._head_angle) <= self.head_recenter_deg:
            self._state = "follow"
        elif self._state == "lost":
            self._state = "follow"

        target = self._target
        error_px = target.center_x - self.image_width / 2.0
        # center margin: person within +/- head_deadband_deg of frame centre
        # -> everything holds still (head AND base, even mid-align)
        deadband_px = self.image_width * self.head_deadband_deg / self.camera_hfov_deg
        within_margin = abs(error_px) <= deadband_px
        head_shift = 0
        if not within_margin:
            error_deg = error_px / self.image_width * self.camera_hfov_deg
            requested = round(self.head_follow_gain * error_deg)
            if requested == 0:
                requested = 1 if error_px > 0.0 else -1
            requested = max(-self.max_head_shift_deg, min(self.max_head_shift_deg, requested))
            target_angle = max(
                -self.head_limit_deg,
                min(self.head_limit_deg, self._head_angle + requested),
            )
            head_shift = int(round(target_angle - self._head_angle))

        angular_z = 0.0
        if (not within_margin
                and self._state == "align"
                and abs(self._head_angle) > self.head_recenter_deg):
            angular_z = (
                self.base_rotation_sign
                * math.copysign(self.base_rotation_speed, self._head_angle)
            )

        return TrackerCommand(
            head_shift=head_shift,
            angular_z=angular_z,
            target_id=target.track_id,
            state=self._state,
        )
