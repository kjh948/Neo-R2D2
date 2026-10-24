"""torch 없이 ncnn 모델 폴더를 직접 구동하는 경량 추론 엔진.

ultralytics 래퍼(RSS 715MB, torch import 2s, 전후처리 코어 소모)를 대체한다.
15Hz detect-only 루프 실측: ultralytics 1.24 cores vs raw ncnn 0.59 cores.

지원: YOLOE/YOLO detect ncnn export 폴더 전반. out0 = (4+nc, anchors) 행 레이아웃은
[cx, cy, w, h, cls_scores...] (앵커 디코드·sigmoid까지 graph 안에서 끝남, 8.4 export 실측).

    from ncnn_engine import NcnnDetect
    det = NcnnDetect("yoloe-26n-det_ncnn_model")
    boxes, scores, cls_ids = det(frame_bgr)     # 프레임 좌표계 xyxy
    annotated = det.draw(frame_bgr, boxes, scores, cls_ids)
"""

import numpy as np
import cv2
import yaml


def load_metadata(model_dir: str) -> dict:
    with open(f"{model_dir}/metadata.yaml", encoding="utf-8") as f:
        return yaml.safe_load(f)


def letterbox(img: np.ndarray, new_hw: tuple[int, int], pad: int = 114):
    """ultralytics utils.ops.LetterBox와 동일 반올림 규격 (세로 박스 1px 어긋남 방지).

    ultralytics: gain=r, nw=round(w0*r), pad=( (W-nw)/2, (H-nh)/2 ) → top/bottom/left/right
    를 int(round(pad ∓ 0.1))로 분리 계산. 이 함수도 같은 식 사용.
    """
    h0, w0 = img.shape[:2]
    H, W = new_hw
    r = min(H / h0, W / w0)
    nw, nh = int(round(w0 * r)), int(round(h0 * r))
    pad_w, pad_h = (W - nw) / 2.0, (H - nh) / 2.0
    top, bottom = int(round(pad_h - 0.1)), int(round(pad_h + 0.1))
    left, right = int(round(pad_w - 0.1)), int(round(pad_w + 0.1))
    resized = cv2.resize(img, (nw, nh), interpolation=cv2.INTER_LINEAR)
    canvas = cv2.copyMakeBorder(resized, top, bottom, left, right,
                                cv2.BORDER_CONSTANT, value=(pad, pad, pad))
    # (resized, gain, (pad_left, pad_top)) — ultralytics letterbox return 규격 매칭
    return canvas, r, (left, top)


class NcnnDetect:
    def __init__(self, model_dir: str, ncnn_threads: int = 2):
        import ncnn  # perf_policy.apply() 이후 import 권장(libgomp env 반영)

        self.meta = load_metadata(model_dir)
        self.names = {int(k): v for k, v in self.meta["names"].items()}
        imgsz = self.meta.get("imgsz") or [256, 320]
        self.hw = (int(imgsz[0]), int(imgsz[1]))  # (H, W)

        self.net = ncnn.Net()
        self.net.opt.num_threads = ncnn_threads
        self.net.opt.use_vulkan_compute = False
        self.net.load_param(f"{model_dir}/model.ncnn.param")
        self.net.load_model(f"{model_dir}/model.ncnn.bin")
        self.in_name = self.net.input_names()[0]
        self.out_name = self.net.output_names()[0]

    def infer_raw(self, img_bgr: np.ndarray):
        """추론만 수행 → (rows, gain, pad_wh, resized_hw) (테스트/ 디버깅용)."""
        lb, gain, pad = letterbox(img_bgr, self.hw)
        blob = np.ascontiguousarray(lb.transpose(2, 0, 1)).astype(np.float32) / 255.0
        import ncnn
        mat = ncnn.Mat(blob)
        with self.net.create_extractor() as ex:
            ex.input(self.in_name, mat)
            _, out = ex.extract(self.out_name)
        return np.array(out), gain, pad  # (4+nc, n_anchors)

    def __call__(self, img_bgr: np.ndarray, conf_thres: float = 0.25, iou_thres: float = 0.7):
        # iou_thres 기본값 0.7 = ultralytics predict 기본 NMS와 동일 (중복 박스 머지 동작 일치)
        """감지 → (boxes(N,4 xyxy 프레임좌표), scores(N,), cls_ids(N,))."""
        rows, gain, (pad_w, pad_h) = self.infer_raw(img_bgr)
        if rows.ndim == 2:
            n_ch, n_anchor = rows.shape
        else:  # (1, C, A) 대비
            rows = rows.reshape(rows.shape[-2], rows.shape[-1])
            n_ch, n_anchor = rows.shape
        scores_all = rows[4:]                                # (nc, A) — 클래스별 sigmoid score
        cls_ids = scores_all.argmax(0)                       # (A,)
        confs = scores_all.max(0)                            # (A,)
        keep = confs >= conf_thres
        if not np.any(keep):
            return np.zeros((0, 4), np.float32), np.zeros((0,), np.float32), np.zeros((0,), np.int32)
        # 행 레이아웃(8.4 ncnn export 실측): [cx, cy, w, h, scores...] (모델 픽셀 좌표)
        cx, cy, w, h = rows[0, keep], rows[1, keep], rows[2, keep], rows[3, keep]
        confs, cls_ids = confs[keep], cls_ids[keep]
        nms_in = np.stack([cx - w / 2, cy - h / 2, w, h], 1).astype(np.float32)  # NMSBoxes용 (x,y,w,h)
        picked = cv2.dnn.NMSBoxes(nms_in.tolist(), confs.tolist(), conf_thres, iou_thres)
        if len(picked) == 0:
            return np.zeros((0, 4), np.float32), np.zeros((0,), np.float32), np.zeros((0,), np.int32)
        idx = np.array(picked, dtype=np.int32).reshape(-1)
        boxes = nms_in[idx]
        boxes = np.column_stack([boxes[:, 0], boxes[:, 1],
                                 boxes[:, 0] + boxes[:, 2], boxes[:, 1] + boxes[:, 3]])  # xyxy

        # letterbox 역변환 → 프레임 좌표
        boxes[:, [0, 2]] = (boxes[:, [0, 2]] - pad_w) / gain
        boxes[:, [1, 3]] = (boxes[:, [1, 3]] - pad_h) / gain
        h0, w0 = img_bgr.shape[:2]
        np.clip(boxes[:, [0, 2]], 0, w0 - 1, out=boxes[:, [0, 2]])
        np.clip(boxes[:, [1, 3]], 0, h0 - 1, out=boxes[:, [1, 3]])
        return boxes, confs[idx].astype(np.float32), cls_ids[idx].astype(np.int32)

    def draw(self, img_bgr: np.ndarray, boxes, scores, cls_ids, thickness: int = 2,
             ids=None, track_colors=False):
        """r.plot() 대체: 사각형+라벨만 얇게 (imshow용).

        ids: 병렬 트래킹 ID 리스트 — 주어지면 라벨에 #N appended.
        track_colors: True면 클래스 대신 ID로 색 고정 (이동체 추적으로 색 유지)
        """
        out = img_bgr.copy()
        palette = [(0, 255, 0), (0, 128, 255), (255, 0, 0), (0, 255, 255),
                   (255, 0, 255), (255, 255, 0), (128, 255, 0), (0, 0, 255)]
        for i, ((x1, y1, x2, y2), s, c) in enumerate(zip(boxes, scores, cls_ids)):
            tid = None if ids is None else int(ids[i])
            color = palette[(tid if (track_colors and tid is not None) else int(c)) % len(palette)]
            label = f"{self.names[int(c)]} {s:.2f}" + (f" #{tid}" if tid is not None else "")
            cv2.rectangle(out, (int(x1), int(y1)), (int(x2), int(y2)), color, thickness)
            cv2.putText(out, label, (int(x1), max(12, int(y1) - 4)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1)
        return out
