#!/usr/bin/env python3
"""ROS-native YOLOE visual-prompt capture (port of ~/workspace/yolo/vp_capture.py).

Frames come from a sensor_msgs/Image topic (v4l2_camera_node's /image_raw)
instead of cv2.VideoCapture, so it works while the rest of the stack owns the
camera.  Output schema is exactly what tools/vp_export.py consumes:
    prompts.json: {"classes": [...], "images": [{"file": ..., "boxes": [[x1,y1,x2,y2,"cls"],...]}]}

조작법 (라이브 미리보기는 최신 프레임만 표시):
  [LIVE]  c = 프레임 freeze → 주석 모드 / q = 종료 (s는 저장 후 종료 아님 — Enter/s 사용 후 q)
  [EDIT]  마우스 드래그 = 현재 클래스 박스 / 1-9 = 클래스 선택
          u = 마지막 박스 취소 / Enter 또는 s = 저장 후 LIVE 복귀 / Esc = 취소
  --grab-only N : GUI 없이 최신 N 프레임만 PNG로 덤프(박스 없음; 원격 headless용)
"""

import json
import os
import time
from datetime import datetime

import numpy as np

WIN = "YOLOE Visual Prompt Capture (ROS)"
PALETTE = [(0, 255, 0), (0, 128, 255), (255, 0, 0), (0, 255, 255),
           (255, 0, 255), (255, 255, 0), (128, 255, 0), (0, 0, 255), (255, 128, 0)]


def load_prompts(path):
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.setdefault("classes", [])
        data.setdefault("images", [])
        return data
    return {"classes": [], "images": []}


def save_prompts(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def msg_to_bgr(msg):
    import cv2
    ch = {"bgr8": 3, "rgb8": 3, "mono8": 1}.get(msg.encoding)
    if ch is None:
        return None
    buf = np.frombuffer(msg.data, np.uint8).reshape(msg.height, msg.step)
    img = buf[:, :msg.width * ch].reshape(msg.height, msg.width, ch)
    if msg.encoding == "rgb8":
        img = img[:, :, ::-1]
    elif msg.encoding == "mono8":
        img = np.repeat(img, 3, axis=2)
    return np.ascontiguousarray(img)


class RosCapturer:
    def __init__(self, classes, out_dir):
        self.out_dir = out_dir
        self.prompts_path = os.path.join(out_dir, "prompts.json")
        os.makedirs(out_dir, exist_ok=True)
        self.data = load_prompts(self.prompts_path)
        for c in classes:
            if c not in self.data["classes"]:
                self.data["classes"].append(c)
        save_prompts(self.prompts_path, self.data)
        self.classes = list(self.data["classes"])
        self.active_cls = 0
        self.latest = None            # newest frame (np uint8 BGR)
        self.frozen = None            # frame being annotated
        self.boxes = []               # [(x1,y1,x2,y2,cls_idx)]
        self.drag_start = self.drag_cur = None

    # ---- ROS side
    def _cb(self, msg):
        img = msg_to_bgr(msg)
        if img is not None:
            self.latest = img

    def _make_node(self):
        import rclpy
        from rclpy.node import Node
        from rclpy.qos import QoSProfile, ReliabilityPolicy
        from sensor_msgs.msg import Image

        class _N(Node):
            def __init__(self, parent, topic):
                super().__init__("vp_capture")
                self.create_subscription(Image, topic, parent._cb,
                                         QoSProfile(depth=1, reliability=ReliabilityPolicy.RELIABLE))
        return rclpy, _N

    # ---- GUI side
    def overlay(self, frame):
        import cv2
        disp = frame.copy()
        h, w = disp.shape[:2]
        for (x1, y1, x2, y2, c) in self.boxes:
            col = PALETTE[c % len(PALETTE)]
            cv2.rectangle(disp, (x1, y1), (x2, y2), col, 2)
            cv2.putText(disp, self.classes[c], (x1, max(15, y1 - 6)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, col, 2)
        if self.frozen is not None and self.drag_start and self.drag_cur:
            (x1, y1), (x2, y2) = self.drag_start, self.drag_cur
            col = PALETTE[self.active_cls % len(PALETTE)]
            cv2.rectangle(disp, (x1, y1), (x2, y2), col, 1)
        mode = "EDIT: drag box / 1-9 class / u undo / Enter save / Esc cancel" \
            if self.frozen is not None else "LIVE: c capture / q quit"
        cv2.rectangle(disp, (0, 0), (w, 26), (30, 30, 30), -1)
        cv2.putText(disp, f"[{self.classes[self.active_cls]}]  {mode}", (6, 18),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1)
        for i, c in enumerate(self.classes[:9]):
            mark = ">" if i == self.active_cls else " "
            cv2.putText(disp, f"{mark}{i + 1}:{c}", (6, 42 + i * 16),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.42, (200, 200, 200), 1)
        return disp

    def on_mouse(self, event, x, y, flags, param):
        import cv2
        if self.frozen is None:
            return
        if event == cv2.EVENT_LBUTTONDOWN:
            self.drag_start = self.drag_cur = (x, y)
        elif event == cv2.EVENT_MOUSEMOVE and self.drag_start:
            self.drag_cur = (x, y)
        elif event == cv2.EVENT_LBUTTONUP and self.drag_start:
            x1, y1 = self.drag_start
            self.drag_start = self.drag_cur = None
            if abs(x - x1) >= 5 and abs(y - y1) >= 5:
                self.boxes.append((min(x1, x), min(y1, y), max(x1, x), max(y1, y), self.active_cls))

    def save_current(self):
        if not self.boxes:
            print("저장할 박스가 없습니다.")
            return
        import cv2
        name = f"img_{datetime.now().strftime('%Y%m%d_%H%M%S')}.png"
        cv2.imwrite(os.path.join(self.out_dir, name), self.frozen)
        self.data["images"].append({
            "file": name,
            "boxes": [[int(b[0]), int(b[1]), int(b[2]), int(b[3]), self.classes[b[4]]]
                      for b in self.boxes]})
        save_prompts(self.prompts_path, self.data)
        print(f"저장: {name} (박스 {len(self.boxes)}, 총 {len(self.data['images'])}장)")

    def run(self, image_topic, grab_only=0):
        import cv2
        rclpy, _N = self._make_node()
        rclpy.init()
        node = _N(self, image_topic)
        try:
            deadline = time.time() + 10.0
            while self.latest is None and time.time() < deadline and rclpy.ok():
                rclpy.spin_once(node, timeout_sec=0.1)
            if self.latest is None:
                print(f"ERROR: {image_topic} 프레임 없음 (카메라/가드 확인)")
                return 1

            if grab_only:
                got, last = 0, None
                t_end = time.time() + 15.0
                while got < grab_only and time.time() < t_end and rclpy.ok():
                    rclpy.spin_once(node, timeout_sec=0.1)
                    if self.latest is not last:
                        last = self.latest
                        name = f"grab_{datetime.now().strftime('%Y%m%d_%H%M%S_%f')}.png"
                        cv2.imwrite(os.path.join(self.out_dir, name), last)
                        got += 1
                        print(f"dump: {name}")
                print(f"{got}/{grab_only} frames dumped to {self.out_dir} "
                      f"(boxes 미포함 — GUI 캡처 또는 수동 annotations 필요)")
                return 0

            cv2.namedWindow(WIN)
            cv2.setMouseCallback(WIN, self.on_mouse)
            print("클래스:", ", ".join(f"{i + 1}:{c}" for i, c in enumerate(self.classes)))
            while True:
                rclpy.spin_once(node, timeout_sec=0.01)
                if self.frozen is None:
                    if self.latest is None:
                        continue
                    disp_src = self.latest
                else:
                    disp_src = self.frozen
                cv2.imshow(WIN, self.overlay(disp_src))
                key = cv2.waitKey(1) & 0xFF
                if key == ord("q"):
                    break
                if self.frozen is None:
                    if key == ord("c"):
                        self.frozen = self.latest.copy()
                        self.boxes = []
                else:
                    if ord("1") <= key <= ord("9") and key - ord("1") < len(self.classes):
                        self.active_cls = key - ord("1")
                    elif key == ord("u"):
                        if self.boxes:
                            self.boxes.pop()
                    elif key in (13, ord("s")):
                        self.save_current()
                        self.frozen = None
                    elif key == 27:
                        self.frozen = None
            cv2.destroyAllWindows()
            return 0
        finally:
            node.destroy_node()
            if rclpy.ok():
                rclpy.shutdown()


def run_capture(classes, out_dir, image_topic="/image_raw", grab_only=0):
    return RosCapturer(classes, out_dir).run(image_topic, grab_only)
