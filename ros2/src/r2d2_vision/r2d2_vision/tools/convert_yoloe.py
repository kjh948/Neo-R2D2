# YOLOE 텍스트 프롬프트 → NCNN export (vendored from ~/workspace/yolo/convert_yoloe.py,
# extended: --classes/--out/--weights 인자화 — vision_export classes 서브커맨드가 호출).
# 실행: <NEO_YOLO_HOME>/venv/bin/python convert_yoloe.py --classes cup,person --out DIR
# (cwd=NEO_YOLO_HOME 에서 돌리면 yoloe-26n.yaml/가중치/mobileclip2_b.ts 가 resolution 됨)
import argparse
import os

from ultralytics import YOLOE

parser = argparse.ArgumentParser(description="YOLOE 텍스트 프롬프트 → NCNN export")
parser.add_argument("--task", choices=["detect", "segment"], default="detect",
                    help="detect: 마스크 헤드 제거(기본, FPS 향상) / segment: seg 원본 유지")
parser.add_argument("--classes", required=True,
                    help="쉼표 구분 클래스명 (공백 금지 — ncnn metadata 제약)")
parser.add_argument("--weights", default="yoloe-26n-seg.pt",
                    help="프롬프트 가능한 YOLOE 가중치 (-pf 금지: set_classes 미지원)")
parser.add_argument("--out", default=None,
                    help="출력 폴더 (상대경로는 cwd 기준; 기본: yoloe-26n-det_ncnn_model / -seg)")
parser.add_argument("--overwrite", action="store_true",
                    help="이미 있는 출력 폴더를 대체")
args = parser.parse_args()

CLASSES = [c.strip() for c in args.classes.split(",") if c.strip()]
assert CLASSES, "--classes 가 비어 있습니다"
for c in CLASSES:
    assert " " not in c, f"클래스명에 공백 금지: '{c}'"

out = args.out or ("yoloe-26n-det_ncnn_model" if args.task == "detect" else "yoloe-26n-seg_ncnn_model")
if os.path.exists(out) and not args.overwrite:
    raise SystemExit(f"{out} 존재. --overwrite 사용 또는 --out 이름 변경.")

if args.task == "detect":
    # detect 아키텍처에 seg 가중치를 로드하면 마스크 브랜치가 자동으로 제거된다
    model = YOLOE("yoloe-26n.yaml").load(args.weights)
else:
    model = YOLOE(args.weights)

model.set_classes(CLASSES)

# export 폴더명 = 가중치 stem + "_ncnn_model". stem을 역산해 --out과 정확히 일치시켜
# 다른 모델 폴더가 덮어써지는 것을 방지한다 (기존 사고 기록).
model.model.pt_path = f"{out.removesuffix('_ncnn_model')}.pt"
exported = model.export(format="ncnn", imgsz=[240, 320], half=True)
if os.path.abspath(exported) != os.path.abspath(out):  # 안전 폴백
    import shutil
    shutil.move(exported, out)
    exported = out
print(f"완료: {exported}/  classes={CLASSES}")
