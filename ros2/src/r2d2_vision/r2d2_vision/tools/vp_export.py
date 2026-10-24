"""vp_capture.py로 저장된 visual prompt를 NCNN 모델로 내보낸다.

순서:
  1. vp_prompts/prompts.json + 이미지 로드
  2. YOLOE.get_visual_pe()로 이미지+박스 → visual prompt embedding 계산
     (같은 클래스의 여러 프롬프트 임베딩은 평균)
  3. model.set_classes(클래스명, embedding)으로 헤드 융합
  4. format="ncnn" export (fp16, 240x320)

사용: venv/bin/python -I vp_export.py [--weights yoloe-26n-seg.pt]
"""

import argparse
import json
import os

import cv2
import numpy as np
import torch
from ultralytics import YOLOE
from ultralytics.data.augment import LoadVisualPrompt


def class_embedding(model, img_bgr, boxes_xyxy, cls_idx, imgsz_hw):
    """한 이미지+박스에서 클래스별 visual prompt embedding (n_cls, D)과 등장한 cls 인덱스 반환."""
    H, W = imgsz_hw
    H = (H + 31) // 32 * 32  # 백본 피라미드 정합을 위해 32 배수로 라운드업
    W = (W + 31) // 32 * 32
    h0, w0 = img_bgr.shape[:2]
    im = cv2.resize(img_bgr, (W, H))
    # 박스 좌표를 리사이즈 배율로 스케일 (xyxy, 픽셀)
    boxes = np.array(boxes_xyxy, dtype=np.float32)
    boxes[:, 0::2] *= W / w0
    boxes[:, 1::2] *= H / h0
    cls = torch.tensor(cls_idx, dtype=torch.int)
    visuals = LoadVisualPrompt().get_visuals(cls, (H, W), bboxes=torch.from_numpy(boxes))  # (n_uniq, H/8, W/8)
    seen = torch.unique(cls, sorted=True).tolist()  # visuals 행 순서 = 정렬된 cls 인덱스
    t = torch.from_numpy(np.ascontiguousarray(im.transpose(2, 0, 1))).float().div_(255)[None]
    vpe = model.get_visual_pe(t, visuals[None])  # (1, n_uniq, D)
    return vpe[0], seen


def main():
    p = argparse.ArgumentParser(description="YOLOE visual prompt → NCNN export")
    p.add_argument("--prompts", default="vp_prompts/prompts.json")
    p.add_argument("--weights", default="yoloe-26n-seg.pt",
                   help="prompt 가능한 YOLOE 가중치 (-pf 금지: visual prompt 미지원)")
    p.add_argument("--imgsz", type=int, nargs=2, default=[240, 320], metavar="[H W]")
    p.add_argument("--out", default="yoloe-vp_ncnn_model", help="출력 NCNN 모델 폴더명")
    p.add_argument("--task", choices=["detect", "segment"], default="detect",
                   help="detect: 마스크 헤드 제거(기본, FPS 향상) / segment: seg 원본 유지")
    p.add_argument("--half", action="store_true", default=True)
    p.add_argument("--overwrite", action="store_true")
    args = p.parse_args()

    with open(args.prompts, encoding="utf-8") as f:
        data = json.load(f)
    if not data.get("images"):
        raise SystemExit("prompts.json에 저장된 이미지가 없습니다. vp_capture.py로 먼저 캡처하세요.")
    classes = data["classes"]
    for c in classes:
        if " " in c:
            raise SystemExit(f"클래스명에 공백 금지: '{c}'")
    print(f"클래스({len(classes)}): {classes}")
    print(f"프롬프트 이미지 {len(data['images'])}개")

    model = YOLOE(args.weights)

    # 클래스별로 embedding 수집 → 평균
    acc = {i: [] for i in range(len(classes))}  # cls_idx -> [(D,)]
    prompt_dir = os.path.dirname(os.path.abspath(args.prompts))
    for rec in data["images"]:
        img = cv2.imread(os.path.join(prompt_dir, rec["file"]))
        if img is None:
            print(f"경고: 이미지 없음, 건너뜀: {rec['file']}")
            continue
        names = [b[4] for b in rec["boxes"]]
        unknown = [n for n in names if n not in classes]
        if unknown:
            raise SystemExit(f"{rec['file']}: 알 수 없는 클래스 {unknown}")
        boxes = [[float(b[0]), float(b[1]), float(b[2]), float(b[3])] for b in rec["boxes"]]
        cls_idx = [classes.index(n) for n in names]
        emb, seen = class_embedding(model, img, boxes, cls_idx, tuple(args.imgsz))
        for j, c in enumerate(seen):
            acc[c].append(emb[j])
        print(f"  {rec['file']}: 클래스 {[classes[x] for x in seen]} embedding 추출")

    used = [i for i in range(len(classes)) if acc[i]]
    if not used:
        raise SystemExit("유효한 embedding이 없습니다.")
    if len(used) < len(classes):
        missing = [classes[i] for i in range(len(classes)) if not acc[i]]
        print(f"경고: 프롬프트 없는 클래스 제외: {missing}")
    names = [classes[i] for i in used]
    pe = torch.stack([torch.stack(acc[i]).mean(0) for i in used])[None]  # (1, nc, D)

    if args.task == "detect":
        # detect 아키텍처에 seg 가중치 로드: 마스크 브랜치가 제거되어 추론/포스트가 가벼워짐
        print("detect 전용 모델로 마스크 브랜치 제거 (yoloe-26n.yaml + seg 가중치)")
        model = YOLOE("yoloe-26n.yaml").load(args.weights)

    print(f"set_classes: {names} (embedding {tuple(pe.shape)})")
    model.set_classes(names, pe)

    # export 폴더명은 가중치 stem에서 파생됨: pt_path를 바꿔 --out 폴더로 직접 쓰게 함
    # (그렇지 않으면 원래 가중치 stem의 *_ncnn_model 폴더를 조용히 덮어씀 — 사고 방지)
    if os.path.exists(args.out) and not args.overwrite:
        raise SystemExit(f"{args.out} 존재. --overwrite 사용.")
    # export 폴더명 = stem + "_ncnn_model" → stem을 역산해 --out 폴더와 정확히 일치시킨다
    model.model.pt_path = f"{args.out.removesuffix('_ncnn_model')}.pt"

    print(f"NCNN export 시작... (task={args.task})")
    exported = model.export(format="ncnn", imgsz=list(args.imgsz), half=args.half)
    if os.path.abspath(exported) != os.path.abspath(args.out):  # 안전 폴백
        import shutil
        shutil.move(exported, args.out)
    print(f"완료: {args.out}/")

    # 간단한 자체 검증: 프롬프트 이미지 하나를 exported 모델로 추론
    probe = cv2.imread(os.path.join(prompt_dir, data["images"][0]["file"]))
    if probe is not None:
        from ultralytics import YOLO
        det = YOLO(args.out)(probe, imgsz=list(args.imgsz), conf=0.4, device="cpu", verbose=False)[0]
        got = [det.names[int(b.cls)] for b in det.boxes]
        print(f"검증({data['images'][0]['file']}): {got or '검출 없음'}")


if __name__ == "__main__":
    main()
