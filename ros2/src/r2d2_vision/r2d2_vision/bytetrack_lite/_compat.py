"""ultralytics 커플링 대체용 소형 shim (LOGGER, xywh2ltwh)."""
import logging

LOGGER = logging.getLogger("bytetrack_lite")


def xywh2ltwh(xywh):
    """(cx, cy, w, h) → (left, top, w, h)."""
    ltwh = xywh.copy()
    ltwh[:2] = xywh[:2] - xywh[2:] / 2
    return ltwh
