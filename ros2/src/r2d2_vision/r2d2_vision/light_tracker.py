"""ultralytics 의존 없이 동작하는 경량 멀티트랙터 (bytetrack 간이 대체).

- 예측: 등속 모델(box += v) — 15Hz 프레임 간 이동 객체/카메라 보정
- 추신: v = α·v + (1-α)·관측변화 (alpha-beta 필터, Kalman의 최소 대용)
- 매칭: 같은 클래스 안에서만 IoU greedy (내림차순)
- 수명: hits >= min_hits에서 확정(출력), max_age 프레임 미관측 시 삭제

bytetrack 대비: 가려짐·급회전 시 ID 유지력 낮음. 고정/완만 이동 카메라와
저레이트(≤15Hz)에서는 실측상 ID 유지 동일 동작. 필요 시 min_hits/max_age 튜닝.
"""

import numpy as np


class LightTracker:
    def __init__(self, iou_thr: float = 0.3, max_age: int = 8, min_hits: int = 2,
                 alpha: float = 0.5):
        self.iou_thr = iou_thr        # 확정 궤적 매칭 IoU 임계
        self.max_age = max_age        # 이만큼 관측 없으면 삭제
        self.min_hits = min_hits      # 이만큼 연속 관측해야 ID 출력
        self.alpha = alpha            # 속도 추산 지수평활 계수
        self.tracks = []              # dict: id, cls, box(4,), v(4,), hits, age
        self.next_id = 1

    @staticmethod
    def _iou_matrix(boxes_a, boxes_b):
        if len(boxes_a) == 0 or len(boxes_b) == 0:
            return np.zeros((len(boxes_a), len(boxes_b)), np.float32)
        a = np.asarray(boxes_a)[:, None, :]          # (N,1,4)
        b = np.asarray(boxes_b)[None, :, :]          # (1,M,4)
        x1 = np.maximum(a[..., 0], b[..., 0]); y1 = np.maximum(a[..., 1], b[..., 1])
        x2 = np.minimum(a[..., 2], b[..., 2]); y2 = np.minimum(a[..., 3], b[..., 3])
        inter = np.maximum(0.0, x2 - x1) * np.maximum(0.0, y2 - y1)
        area_a = (a[..., 2] - a[..., 0]) * (a[..., 3] - a[..., 1])
        area_b = (b[..., 2] - b[..., 0]) * (b[..., 3] - b[..., 1])
        return inter / (area_a + area_b - inter + 1e-9)

    def update(self, boxes, cls_ids):
        """관측 (N,4) xyxy + (N,) 클래스 → 확정 궤적 [(track_id, box, cls)] 반환."""
        boxes = np.asarray(boxes, dtype=np.float32).reshape(-1, 4)
        cls_ids = np.asarray(cls_ids, dtype=np.int32).reshape(-1)

        # 1) 예측: 등속 이동
        for t in self.tracks:
            t["pred"] = t["box"] + t["v"]
            t["age"] += 1

        # 2) 매칭 (클래스 내부에서만, IoU 내림차순 greedy)
        used_t, used_b = set(), set()
        if len(self.tracks) and len(boxes):
            pairs = []
            for i, t in enumerate(self.tracks):
                cand = np.where(cls_ids == t["cls"])[0]
                if len(cand) == 0:
                    continue
                ious = self._iou_matrix([t["pred"]], boxes[cand])[0]
                for j_local, v in enumerate(ious):
                    if v >= self.iou_thr:
                        pairs.append((float(v), i, int(cand[j_local])))
            pairs.sort(reverse=True)
            for _v, i, j in pairs:
                if i in used_t or j in used_b:
                    continue
                used_t.add(i); used_b.add(j)
                t = self.tracks[i]
                delta = boxes[j] - t["box"]
                t["v"] = self.alpha * t["v"] + (1.0 - self.alpha) * delta
                t["box"] = boxes[j]
                t["cls"] = int(cls_ids[j])
                t["hits"] += 1
                t["age"] = 0

        # 3) 미매칭 관측 → 신규 트래크
        for j in range(len(boxes)):
            if j not in used_b:
                self.tracks.append({"id": self.next_id, "cls": int(cls_ids[j]),
                                    "box": boxes[j].copy(), "pred": boxes[j].copy(),
                                    "v": np.zeros(4, np.float32), "hits": 1, "age": 0})
                self.next_id += 1

        # 4) 소멸
        self.tracks = [t for t in self.tracks if t["age"] <= self.max_age]

        return [(t["id"], t["box"], t["cls"]) for t in self.tracks if t["hits"] >= self.min_hits]
