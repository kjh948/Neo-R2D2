#!/usr/bin/env python3
"""vision_export — offline YOLOE class-set tooling + live node control.

Subcommands (run with system python after sourcing the workspace):

  vision_export classes --classes "cup,person,door" [--out NAME] [--task detect] [--switch]
      Text-prompt export: bakes the class embeddings into a new *_ncnn_model
      folder via the ultralytics/torch stack in the yolo venv (build-time only —
      the ROS node itself never imports torch).  --switch also hot-loads it into
      the running node through /yolo/switch_model.

  vision_export vp-capture --classes cup,person [--out DIR] [--image-topic /image_raw]
      ROS-native visual-prompt capture (replaces ~/workspace/yolo/vp_capture.py's
      cv2.VideoCapture path): annotates frames coming off /image_raw with mouse
      boxes and accumulates them into DIR/prompts.json (schema consumed by
      vp-export).  Needs a display; --grab-only N dumps N frames instead.

  vision_export vp-export --prompts DIR/prompts.json --out NAME [--task detect] [--overwrite]
      Visual-prompt export (venv torch path), self-verifies on a prompt image.

  vision_export switch --model PATH        hot-swap the live node's model folder
  vision_export list                       live node + its current model/classes

Model folders are NOT packaged into the ROS share dir; they live under
$NEO_YOLO_MODEL_HOME (default /home/kjh948/workspace/yolo) — the venv, weights
(yoloe-26n-seg.pt) and mobileclip2_b.ts must be there too ($NEO_YOLO_HOME).

Gotchas encoded from the yolo README pitfall log:
  - class names must contain NO spaces (ncnn metadata constraint)
  - export folder derives from the weight stem — we pin it, so an existing
    folder is only overwritten with --overwrite
  - imgsz is rounded up to multiples of 32 inside the exporter
  - -pf (prompt-free) weights cannot be re-classed — use yoloe-26n-seg.pt
"""

import argparse
import json
import os
import subprocess
import sys

YOLO_HOME = os.environ.get("NEO_YOLO_HOME", "/home/kjh948/workspace/yolo")
MODEL_HOME = os.environ.get("NEO_YOLO_MODEL_HOME", YOLO_HOME)
def _tools_dir():
    d = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tools")
    if os.path.isdir(d):  # source layout / symlink-install
        return d
    from ament_index_python.packages import get_package_share_directory
    return os.path.join(get_package_share_directory("r2d2_vision"), "tools")


TOOLS_DIR = _tools_dir()


def _venv_python():
    py = os.path.join(YOLO_HOME, "venv", "bin", "python")
    if not os.path.isfile(py):
        sys.exit(f"ERROR: ultralytics venv not found at {py}\n"
                 f"       export NEO_YOLO_HOME=<dir containing venv/ and the yoloe weights>")
    return py


def _normalize_classes(raw):
    classes = [c.strip().replace(" ", "_") for c in raw.split(",") if c.strip()]
    if not classes:
        sys.exit("ERROR: no classes given")
    bad = [c for c in classes if not c.replace("_", "").isalnum()]
    if bad:
        sys.exit(f"ERROR: class names must be alphanumeric/underscore: {bad}")
    return classes


# ------------------------------------------------------------------ classes
def cmd_classes(args):
    classes = _normalize_classes(args.classes)
    out = args.out or ("vision-" + "-".join(classes[:3]) + "_ncnn_model")
    if not out.endswith("_ncnn_model"):
        out += "_ncnn_model"
    out_dir = os.path.join(MODEL_HOME, out)
    if os.path.exists(out_dir) and not args.overwrite:
        sys.exit(f"ERROR: {out_dir} exists — pass --overwrite to replace it")
    # The vendored convert_yoloe.py (package tools/) takes --classes/--out — the
    # copy in ~/workspace/yolo hardcodes them.  cwd=YOLO_HOME so
    # yoloe-26n.yaml / yoloe-26n-seg.pt / mobileclip2_b.ts resolve.
    if args.overwrite:
        cmd_extra = ["--overwrite"]
    else:
        cmd_extra = []
    cmd = [_venv_python(), os.path.join(TOOLS_DIR, "convert_yoloe.py"),
           "--task", args.task, "--out", out_dir,
           "--classes", ",".join(classes)] + cmd_extra
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=YOLO_HOME, check=True)
    print(f"model ready: {out_dir}")
    if args.switch:
        _switch_model(out_dir)


# ---------------------------------------------------------------- vp-capture
def cmd_vp_capture(args):
    classes = _normalize_classes(args.classes) if args.classes else []
    if not classes:
        sys.exit("ERROR: --classes cup,person,... is required")
    try:
        import cv2  # noqa: F401
        import rclpy
    except ImportError as e:
        sys.exit(f"ERROR: capture needs system-python cv2+rclpy sourced: {e}")
    from r2d2_vision.vp_capture_ros import run_capture
    sys.exit(run_capture(classes=classes, out_dir=args.out, image_topic=args.image_topic,
                         grab_only=args.grab_only))


# ------------------------------------------------------------------ vp-export
def cmd_vp_export(args):
    prompts = os.path.abspath(args.prompts)
    if not os.path.isfile(prompts):
        sys.exit(f"ERROR: {prompts} not found (run vision_export vp-capture first)")
    out = args.out or "yoloe-vp_ncnn_model"
    if not out.endswith("_ncnn_model"):
        out += "_ncnn_model"
    out_dir = os.path.join(MODEL_HOME, out)
    if os.path.exists(out_dir) and not args.overwrite:
        sys.exit(f"ERROR: {out_dir} exists — pass --overwrite")
    script = os.path.join(TOOLS_DIR, "vp_export.py")  # vendored copy = single source of truth
    cmd = [_venv_python(), script, "--prompts", prompts,
           "--out", out_dir, "--task", args.task]
    if args.overwrite:
        cmd.append("--overwrite")
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=YOLO_HOME, check=True)
    print(f"model ready: {out_dir}")
    if args.switch:
        _switch_model(out_dir)


# ------------------------------------------------------------- switch / list
def _switch_model(path, service="yolo/switch_model"):
    import rclpy
    from rclpy.node import Node
    from yolo_msgs.srv import SwitchModel
    rclpy.init()
    node = Node("vision_export_client")
    cli = node.create_client(SwitchModel, service)
    if not cli.wait_for_service(timeout_sec=5.0):
        node.destroy_node()
        rclpy.shutdown()
        sys.exit(f"ERROR: service {service} not found — is detection_node running?")
    req = SwitchModel.Request()
    req.model_path = path
    fut = cli.call_async(req)
    rclpy.spin_until_future_complete(node, fut, timeout_sec=15.0)
    if fut.result() is None:
        sys.exit("ERROR: no service response (model load may still be running; check node log)")
    ok, msg = fut.result().success, fut.result().message
    print(("OK: " if ok else "FAIL: ") + msg)
    node.destroy_node()
    rclpy.shutdown()
    return 0 if ok else 1


def cmd_switch(args):
    path = os.path.abspath(args.model)
    if not os.path.isabs(args.model):
        path = os.path.join(MODEL_HOME, args.model)
    sys.exit(_switch_model(path))


def cmd_list(args):
    import rclpy
    from rclpy.node import Node
    from yolo_msgs.msg import DetectionArray
    rclpy.init()
    node = Node("vision_export_list")
    state = {}

    def cb(m):
        state["classes"] = sorted({d.class_name for d in m.detections})
        state["n"] = len(m.detections)

    sub = node.create_subscription(DetectionArray, "yolo/detections", cb, 10)
    print("listening on yolo/detections for 3 s ...")
    import time
    t0 = time.time()
    while time.time() - t0 < 3.0 and rclpy.ok():
        rclpy.spin_once(node, timeout_sec=0.1)
    if "classes" in state:
        print(f"node is live: {state['n']} detections in last msg, classes seen: {state['classes']}")
    else:
        print("no yolo/detections traffic (node not running or no frames yet)")
    node.destroy_node()
    rclpy.shutdown()


def main():
    ap = argparse.ArgumentParser(prog="vision_export", description=__doc__.splitlines()[0],
                                 formatter_class=argparse.RawDescriptionHelpFormatter,
                                 epilog=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)

    a = sub.add_parser("classes", help="text-prompt export → new *_ncnn_model")
    a.add_argument("--classes", required=True, help="comma-separated, no spaces")
    a.add_argument("--out", default=None, help="model folder name")
    a.add_argument("--task", choices=["detect", "segment"], default="detect")
    a.add_argument("--switch", action="store_true", help="hot-load into the live node")
    a.add_argument("--overwrite", action="store_true")
    a.set_defaults(fn=cmd_classes)

    a = sub.add_parser("vp-capture", help="ROS-native visual prompt capture (GUI or --grab-only)")
    a.add_argument("--classes", required=True)
    a.add_argument("--out", default=os.path.join(MODEL_HOME, "vp_prompts"))
    a.add_argument("--image-topic", default="/image_raw")
    a.add_argument("--grab-only", type=int, default=0, help="dump N frames, no GUI")
    a.set_defaults(fn=cmd_vp_capture)

    a = sub.add_parser("vp-export", help="visual prompts → *_ncnn_model (venv torch)")
    a.add_argument("--prompts", required=True)
    a.add_argument("--out", default=None)
    a.add_argument("--task", choices=["detect", "segment"], default="detect")
    a.add_argument("--switch", action="store_true")
    a.add_argument("--overwrite", action="store_true")
    a.set_defaults(fn=cmd_vp_export)

    a = sub.add_parser("switch", help="hot-swap the live node's model folder")
    a.add_argument("--model", required=True, help="abs path or name under NEO_YOLO_MODEL_HOME")
    a.set_defaults(fn=cmd_switch)

    a = sub.add_parser("list", help="show live node status from yolo/detections")
    a.set_defaults(fn=cmd_list)

    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
