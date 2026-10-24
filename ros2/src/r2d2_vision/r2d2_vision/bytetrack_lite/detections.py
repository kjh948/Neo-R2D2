"""BYTETracker가 요구하는 detections 인터페이스 어댑터.

ultralytics의 `result.boxes.cpu().numpy()`가 던지는 Boxes 객체와 동일한 표면
(`.conf`/`.cls`/`.xywh`/`.xyxy`, 불리언 인덱싱, len)만 구현한 torch-free 최소본.
ncnn_engine의 (xyxy, scores, cls) 출력 → BYTETracker 입력 변환에 쓴다.
"""

from __future__ import annotations

import numpy as np


class Detections:
    def __init__(self, xyxy, conf, cls):
        self.xyxy = np.asarray(xyxy, dtype=np.float32).reshape(-1, 4)
        self.conf = np.asarray(conf, dtype=np.float32).reshape(-1)
        self.cls = np.asarray(cls).reshape(-1).astype(np.int64)
        x1, y1, x2, y2 = self.xyxy.T
        self.xywh = np.stack([(x1 + x2) / 2, (y1 + y2) / 2, x2 - x1, y2 - y1], axis=1).astype(np.float32)

    def __len__(self):
        return len(self.conf)

    def __getitem__(self, idx):
        idx = np.asarray(idx)
        return Detections(self.xyxy[idx], self.conf[idx], self.cls[idx])
