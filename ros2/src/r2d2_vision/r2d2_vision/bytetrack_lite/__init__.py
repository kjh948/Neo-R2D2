"""ultralytics/torch 없이 동작하는 ByteTrack (vendored from ultralytics 8.4.182 trackers, AGPL-3.0).

vendoring 대상: byte_tracker/basetrack/utils(kalman_filter, stracks, matching) — 알고리즘 코드
그대로라 ultralytics mode="track"의 bytetrack과 **동일 품질/동일 ID 동작**. 커플링만 제거:
LOGGER→logging, metrics.batch_probiou→미사용(obb) 삭제, ops.linear_sum_assignment→_geom(numpy JV),
입력은 detections.Detections 어댑터(xyxy,conf,cls)로 공급.

사용:
    from bytetrack_lite import BYTETracker, Detections, make_tracker
    tracker = make_tracker(frame_rate=15)
    rows = tracker.update(Detections(boxes, scores, cls), frame_bgr)
"""

from types import SimpleNamespace

from .byte_tracker import BYTETracker, STrack
from .detections import Detections

# ultralytics/cfg/trackers/bytetrack.yaml 기본값 (8.4.182)
DEFAULTS = dict(tracker_type="bytetrack", track_high_thresh=0.25, track_low_thresh=0.1,
                new_track_thresh=0.25, track_buffer=30, match_thresh=0.8, fuse_score=True,
                frame_rate=30, gmc_method="sparseOptFlow")


def make_tracker(**overrides) -> BYTETracker:
    return BYTETracker(SimpleNamespace(**{**DEFAULTS, **overrides}))


__all__ = ["BYTETracker", "STrack", "Detections", "make_tracker", "DEFAULTS"]
