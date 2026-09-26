"""Model output text -> waypoints + protocol signals (pure numpy, vendored LightNav).

Replicates the RVQ branch of lightnav.tracking.TrackingAgent.decode_waypoints and
lightnav.serving.protocol's signal/pointing/actions payload builders, so the wire
response is identical to ``lightnav-serve``.
"""

from __future__ import annotations

import numpy as np

from lightnav.serving.protocol import (
    actions_payload,
    decode_prediction_signals,
    pointing_payload,
)
from lightnav.traj_vocab import RVQBundle
from lightnav.velocity import is_stop_centroid
from lightnav.vln_utils import parse_rvq_action_tokens

RVQ_STOP_ATOL = 5e-3  # lightnav.tracking._RVQ_STOP_ATOL


class RvqDecoder:
    def __init__(self, bundle: RVQBundle):
        self.bundle = bundle
        self.H = int(bundle.horizon)

    def decode_waypoints(self, text: str) -> np.ndarray:
        """``<act_l*>`` tokens -> (H, 3) float32 waypoints; stop -> exact zeros.

        Raises ValueError (server maps to rc 500, connection stays open) when the
        level set is incomplete or a code is out of its codebook range.
        """
        codes = parse_rvq_action_tokens(text)
        levels = self.bundle.levels
        if len(codes) != len(levels):
            raise ValueError(f"got {len(codes)} act levels, expected {len(levels)} from {text!r}")
        for lvl, c in enumerate(codes):
            if not (0 <= c < levels[lvl]):
                raise ValueError(f"rvq code {c} at level {lvl} out of range [0, {levels[lvl]})")
        if self.bundle.is_stop(codes):
            return np.zeros((self.H, 3), dtype=np.float32)
        wp = self.bundle.decode_waypoints(codes)
        if is_stop_centroid(wp, atol=RVQ_STOP_ATOL):
            wp = np.zeros((self.H, 3), dtype=np.float32)
        return np.asarray(wp, dtype=np.float32)

    def build_response_data(
        self, text: str, waypoints: np.ndarray, step: int, client_frame_hw: tuple[int, int]
    ) -> dict:
        """Wire `data` dict for a successful `next`, matching lightnav protocol."""
        sig = decode_prediction_signals(text, is_rvq=True, waypoints=waypoints)
        data: dict = {
            "rc": 0,
            "seq": None,  # caller fills
            "actions": actions_payload(waypoints, step),
            "stop": bool(sig.stop),
            "visible": sig.visible,
            "raw_text": text[:256],
        }
        pt = pointing_payload(text, width=client_frame_hw[1], height=client_frame_hw[0])
        if pt is not None:
            data["pointing"] = pt
        return data
