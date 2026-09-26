"""Per-session observation buffer + SlowFast tier sampling.

Mirrors lightnav's ``NavigationPolicy`` (slowfast mode): the whole episode is
kept, absolute frame ids drive the tier sampler, and each decision step yields
chronologically ordered segments of even-length frame pairs. The heavy lifting
is done by the vendored ``lightnav.slowfast`` module so the tier layout stays
byte-identical to the trained one.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

import numpy as np

from lightnav.slowfast import slowfast_segments, validate_slowfast_tiers


class EpisodeBuffer:
    """Ring of raw HWC uint8 RGB frames for one client session."""

    def __init__(self, tiers: List[Dict[str, Any]], max_frames: int = 4096):
        self.tiers = validate_slowfast_tiers(tiers)
        self.max_frames = max_frames
        self.frames: List[np.ndarray] = []   # index == absolute episode frame id
        self._abs_base = 0                   # frames[0]'s absolute id (after ring trim)

    def reset(self) -> None:
        self.frames.clear()
        self._abs_base = 0

    def append(self, frame: np.ndarray) -> int:
        """Store one frame; returns its absolute episode id."""
        self.frames.append(frame)
        if len(self.frames) > self.max_frames:
            drop = len(self.frames) - self.max_frames
            del self.frames[:drop]
            self._abs_base += drop
        return self.current_abs

    @property
    def current_abs(self) -> int:
        return self._abs_base + len(self.frames) - 1

    def __len__(self) -> int:
        return len(self.frames)

    def n_available(self) -> int:
        """Frames available going back from current (capped by the sampler to
        what the tier plan can actually use; pass the true count here)."""
        return len(self.frames)

    def get(self, abs_id: int) -> Optional[np.ndarray]:
        i = abs_id - self._abs_base
        return self.frames[i] if 0 <= i < len(self.frames) else None

    def sample_segments(self) -> List[Dict[str, Any]]:
        """Tier segments for the current step: oldest first, each with even
        ``frame_ids`` (absolute ids), ``pool_spatial``, ``pool_mode``, ``tier``.

        Caps n_available to what the deepest tier can use (age_hi is finite per
        tier; the span/anchor math tolerates short episodes), so a long episode
        doesn't force the sampler to walk thousands of ids.
        """
        if not self.frames:
            raise RuntimeError("empty observation buffer")
        max_age = 1
        for t in self.tiers:
            if t["mode"] == "dense":
                max_age = max(max_age, int(t["age_hi"]) + 1)
            elif t["mode"] == "burst":
                span = int(t.get("pair_stride", 1)) * int(t.get("num_pairs", 0))
                max_age = max(max_age, int(t["age_hi"]) + 1, span + 2)
            elif t["mode"] == "span":
                max_age = max(max_age, int(t["age_hi"]) + 1)
            # anchor: absolute episode-start frames; capped by num_frames.
            if t["mode"] == "anchor":
                max_age = max(max_age, int(t["num_frames"]))
        n_avail = min(self.n_available(), max_age)
        return slowfast_segments(self.current_abs, n_avail, self.tiers)
