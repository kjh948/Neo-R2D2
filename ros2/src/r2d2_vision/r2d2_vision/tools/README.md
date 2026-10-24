# tools/ — offline YOLOE export scripts (torch side)

These two scripts are **not imported by the ROS node** at runtime. They are the
single source of truth for building class-specialized NCNN model folders and are
invoked by `vision_export classes` / `vision_export vp-export`, which run them
with the **ultralytics venv** python:

    /home/kjh948/workspace/yolo/venv/bin/python   (env NEO_YOLO_HOME to relocate)

- `convert_yoloe.py` — text prompts → baked embeddings → `*_ncnn_model`
  (vendored from ~/workspace/yolo, extended with `--classes/--out/--weights/--overwrite`)
- `vp_export.py` — visual prompts (`vp_prompts/prompts.json`, schema
  `{"classes": [...], "images": [{"file", "boxes": [[x1,y1,x2,y2,"cls"],...]}]}`)
  → `*_ncnn_model` (verbatim from ~/workspace/yolo)

Requirements when running directly: cwd = NEO_YOLO_HOME (weights
`yoloe-26n-seg.pt`, `yoloe-26n.yaml`, `mobileclip2_b.ts` resolve there),
imgsz stays `[240, 320]` (embedding extraction rounds up to 32-multiples
internally), class names without spaces, `-pf` prompt-free weights cannot be
re-classed. Prefer calling through `vision_export` — it encodes all of that.
