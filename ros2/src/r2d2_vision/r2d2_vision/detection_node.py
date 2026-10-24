#!/usr/bin/env python3
"""NCNN YOLO detection/tracking node — yolo_ros-flavored interface.

Subscribes to a sensor_msgs/Image (v4l2_camera_node @ 30 Hz by convention) and
runs the torch-free NCNN stack validated in ~/workspace/yolo at a fixed
inference rate (default 15 Hz).  Latest-frame-only slot: the worker always
processes the newest arrival and never queues, so latency cannot accumulate
and a slow inference just drops ticks.

Topics (namespace `yolo` by default):
    yolo/detections   yolo_msgs/DetectionArray   (id == "")
    yolo/tracking     yolo_msgs/DetectionArray   (id == str(track_id))
    yolo/debug_image  sensor_msgs/Image bgr8     (boxes + labels, 15 Hz)
Service:
    yolo/switch_model yolo_msgs/srv/SwitchModel  (hot-swap ncnn model folder;
        resets the tracker — ids/embeddings are model-scoped)

Runtime deps: rclpy, numpy, cv2, ncnn (pip --user, system python!), yaml.
The node runs on /usr/bin/python3; do NOT launch it from the yolo venv
(numpy 2.x there breaks the numpy-1-ABI rosidl extensions).
"""

import os
import threading

import numpy as np
import rclpy
from rclpy.node import Node
from rclpy.qos import QoSProfile, ReliabilityPolicy
from sensor_msgs.msg import Image
from yolo_msgs.msg import Detection, DetectionArray
from yolo_msgs.srv import SwitchModel

# Import order matters: ncnn_engine pulls in cv2, and perf_policy must set
# OMP_WAIT_POLICY before libgomp loads — perf_policy.apply() runs in main()
# and the engine class is imported lazily inside the constructor.

_RELIABILITY = {0: ReliabilityPolicy.BEST_EFFORT, 1: ReliabilityPolicy.RELIABLE,
                2: ReliabilityPolicy.SYSTEM_DEFAULT}


class ImageSlot:
    """Single latest-frame hand-off — overwrite, never queue."""

    def __init__(self):
        self._cond = threading.Condition()
        self._frame = None
        self._tick = 0              # bumped ONLY by the max_fps timer
        self._consumed_tick = 0

    def push(self, item):
        with self._cond:
            self._frame = item

    def notify(self):
        with self._cond:
            self._tick += 1
            self._cond.notify_all()

    def wait_for_work(self, stop, timeout=0.5):
        with self._cond:
            return self._cond.wait_for(
                lambda: stop.is_set() or self._tick != self._consumed_tick, timeout)

    def take_latest(self):
        """Newest frame on an unconsumed timer tick, else None.

        Gating on TICKS (not frame arrival) caps inference at max_fps even when
        the camera pushes faster — a pending frame without a tick must not
        wake the worker (arrival-gating measured 23-25 Hz on a 30 Hz feed).
        """
        with self._cond:
            if self._tick == self._consumed_tick or self._frame is None:
                return None
            self._consumed_tick = self._tick
            return self._frame


class DetectionNode(Node):

    def __init__(self):
        super().__init__("detection_node")

        self.declare_parameter("model_path",
                               os.environ.get("NEO_YOLO_MODEL_HOME",
                                              "/home/kjh948/workspace/yolo")
                               + "/yoloe-26n-det_ncnn_model")
        self.declare_parameter("threshold", 0.4)
        self.declare_parameter("iou", 0.7)
        self.declare_parameter("max_fps", 15.0)
        self.declare_parameter("use_tracking", True)
        self.declare_parameter("tracker_type", "bytetrack")  # or "light"
        # unset-typed: an [] default would bind the param to BYTE_ARRAY and reject
        # STRING_ARRAY overrides; yaml `[]` is also dropped by rcl on Jazzy entirely.
        self.declare_parameter("class_filter", rclpy.Parameter.Type.STRING_ARRAY)
        self.declare_parameter("debug_image", True)
        self.declare_parameter("image_topic", "/image_raw")
        self.declare_parameter("image_reliability", 1)       # 0 best_effort 1 reliable 2 system
        self.declare_parameter("ncnn_threads", 2)
        self.declare_parameter("track_high_thresh", 0.25)
        self.declare_parameter("track_low_thresh", 0.1)
        self.declare_parameter("new_track_thresh", 0.25)
        self.declare_parameter("track_buffer", 30)
        self.declare_parameter("match_thresh", 0.8)

        p = self.get_parameter
        self.model_path = p("model_path").value
        self.threshold = float(p("threshold").value)
        self.iou = float(p("iou").value)
        self.max_fps = max(0.5, float(p("max_fps").value))
        self.use_tracking = bool(p("use_tracking").value)
        self.tracker_type = str(p("tracker_type").value)
        self.class_filter = set(p("class_filter").value or [])
        self.debug_enabled = bool(p("debug_image").value)
        self.ncnn_threads = int(p("ncnn_threads").value)
        image_topic = str(p("image_topic").value)
        reliability = _RELIABILITY.get(int(p("image_reliability").value),
                                       ReliabilityPolicy.RELIABLE)

        try:
            from r2d2_vision.ncnn_engine import NcnnDetect
        except ImportError as e:
            raise RuntimeError(
                "ncnn import failed — install the wheel for system python:\n"
                "  /usr/bin/python3 -m pip install --user --break-system-packages "
                "--no-deps ncnn==1.0.20260526\n"
                "(--no-deps is mandatory: a user-site numpy>=2 would break every "
                f"ROS python binding). Underlying error: {e}") from e
        self._NcnnDetect = NcnnDetect
        self.engine = self._load_model(self.model_path)
        self.tracker = self._make_tracker() if self.use_tracking else None

        self.pub_detections = self.create_publisher(DetectionArray, "detections", 10)
        self.pub_tracking = self.create_publisher(DetectionArray, "tracking", 10)
        if self.debug_enabled:
            self.pub_debug = self.create_publisher(Image, "debug_image", 10)

        self.create_service(SwitchModel, "switch_model", self._on_switch_model)

        self.slot = ImageSlot()
        self._stop = threading.Event()
        self._pending_model = None
        self._warned_encodings = set()
        qos = QoSProfile(depth=1, reliability=reliability)
        self.create_subscription(Image, image_topic, self._on_image, qos)
        self.create_timer(1.0 / self.max_fps, self.slot.notify)
        self._worker = threading.Thread(target=self._run, daemon=True, name="yolo_worker")
        self._worker.start()

        self.get_logger().info(
            f"ready: model={self.model_path} "
            f"classes={sorted(self.engine.names.values())} "
            f"inference={self.max_fps:.1f}Hz "
            f"tracking={self.tracker_type if self.use_tracking else 'off'} "
            f"subscribe={image_topic}({reliability.name})")

    # ------------------------------------------------------------------ model
    def _load_model(self, path):
        if not os.path.isfile(os.path.join(path, "metadata.yaml")):
            raise RuntimeError(f"not an ncnn model folder (no metadata.yaml): {path}")
        return self._NcnnDetect(path, ncnn_threads=self.ncnn_threads)

    def _make_tracker(self):
        if self.tracker_type == "light":
            from r2d2_vision.light_tracker import LightTracker
            return LightTracker()
        from r2d2_vision.bytetrack_lite import make_tracker
        # gmc_method="" — sparseOptFlow would burn a core on full-frame optical
        # flow; the measured ~0.6-core budget assumed GMC off.
        return make_tracker(
            frame_rate=self.max_fps, gmc_method="",
            track_high_thresh=float(self.get_parameter("track_high_thresh").value),
            track_low_thresh=float(self.get_parameter("track_low_thresh").value),
            new_track_thresh=float(self.get_parameter("new_track_thresh").value),
            track_buffer=int(self.get_parameter("track_buffer").value),
            match_thresh=float(self.get_parameter("match_thresh").value))

    # --------------------------------------------------------------------- io
    def _on_image(self, msg: Image):
        self.slot.push(msg)

    def _to_bgr(self, msg: Image):
        channels = {"bgr8": 3, "rgb8": 3, "mono8": 1}
        if msg.encoding not in channels:
            if msg.encoding not in self._warned_encodings:
                self._warned_encodings.add(msg.encoding)
                self.get_logger().error(
                    f"unsupported image encoding {msg.encoding!r} "
                    f"(supported: bgr8/rgb8/mono8) — frames ignored until it changes")
            return None
        ch = channels[msg.encoding]
        # msg.step is BYTES per row — reshape rows of bytes, then drop row padding
        buf = np.frombuffer(msg.data, dtype=np.uint8).reshape(msg.height, msg.step)
        img = buf[:, :msg.width * ch].reshape(msg.height, msg.width, ch)
        if msg.encoding == "rgb8":
            img = img[:, :, ::-1]
        elif msg.encoding == "mono8":
            img = np.repeat(img, 3, axis=2)
        return np.ascontiguousarray(img)

    def _on_switch_model(self, request, response):
        path = request.model_path
        if not os.path.isfile(os.path.join(path, "metadata.yaml")):
            response.success = False
            response.message = f"not an ncnn model folder (no metadata.yaml): {path}"
            return response
        self._pending_model = path
        response.success = True
        response.message = f"switching to {path} on the next inference tick"
        return response

    # ------------------------------------------------------------------ worker
    def _run(self):
        # Warmup: first real inference costs +100-300 ms of lazy ncnn allocs —
        # absorb it (and the model load) before we can be fed real frames.
        dummy = np.zeros((self.engine.hw[0], self.engine.hw[1], 3), np.uint8)
        for _ in range(2):
            self.engine(dummy, 0.99, self.iou)
        while not self._stop.is_set():
            self.slot.wait_for_work(self._stop)
            if self._stop.is_set():
                break
            msg = self.slot.take_latest()
            if msg is None:
                continue
            if self._pending_model is not None:
                self._apply_switch(self._pending_model)
                self._pending_model = None
            img = self._to_bgr(msg)
            if img is None:
                continue
            try:
                self._infer_and_publish(img, msg)
            except Exception as e:  # never kill the loop on a bad frame
                self.get_logger().warning(f"inference/publish tick failed: {e!r}")

    def _apply_switch(self, path):
        try:
            engine = self._load_model(path)
        except Exception as e:
            self.get_logger().error(f"switch_model to {path} failed: {e} — keeping old model")
            return
        self.engine = engine
        self.tracker = self._make_tracker() if self.use_tracking else None
        self.model_path = path
        self.get_logger().info(
            f"model switched to {path}: classes={sorted(engine.names.values())} "
            f"(tracker reset, ids not carried over)")

    def _infer_and_publish(self, img, msg):
        boxes, scores, cls_ids = self.engine(img, self.threshold, self.iou)
        names = self.engine.names

        if self.class_filter:
            keep = np.array([names[int(c)] in self.class_filter for c in cls_ids]) \
                if len(cls_ids) else np.zeros(0, bool)
            boxes, scores, cls_ids = boxes[keep], scores[keep], cls_ids[keep]

        track_ids = None
        if self.use_tracking and self.tracker is not None:
            if self.tracker_type == "light":
                obs_boxes, obs_scores, obs_cls = boxes, scores, cls_ids
                rows = self.tracker.update(boxes, cls_ids)  # [(id, box, cls)]
                if len(rows):
                    ids, bx, cl = zip(*rows)
                    boxes = np.asarray(bx, np.float32)
                    cls_ids = np.asarray(cl, np.int32)
                    scores = np.asarray([self._match_score(obs_boxes, obs_scores, obs_cls,
                                                           b, c)
                                         for b, c in zip(boxes, cls_ids)], np.float32)
                    track_ids = [int(i) for i in ids]
                else:
                    boxes = np.zeros((0, 4), np.float32)
                    cls_ids = np.zeros(0, np.int32)
                    scores = np.zeros(0, np.float32)
                    track_ids = []
            else:
                from r2d2_vision.bytetrack_lite import Detections
                det = Detections(boxes, scores, cls_ids)
                res = self.tracker.update(det, img)  # (N,8): x1,y1,x2,y2,id,score,cls,idx
                if len(res):
                    boxes = res[:, :4].astype(np.float32)
                    track_ids = res[:, 4].astype(np.int64)
                    scores = res[:, 5].astype(np.float32)
                    cls_ids = res[:, 6].astype(np.int32)
                else:
                    track_ids = np.zeros(0, np.int64)
                    boxes = np.zeros((0, 4), np.float32)
                    scores = np.zeros(0, np.float32)
                    cls_ids = np.zeros(0, np.int32)

        header = msg.header
        self.pub_detections.publish(self._array_msg(header, boxes, scores, cls_ids, names, None))
        if self.use_tracking and self.tracker is not None:
            self.pub_tracking.publish(
                self._array_msg(header, boxes, scores, cls_ids, names, track_ids))
        if self.debug_enabled:
            annotated = self.engine.draw(img, boxes, scores, cls_ids,
                                         ids=track_ids, track_colors=track_ids is not None)
            self.pub_debug.publish(self._image_msg(header, annotated))

    @staticmethod
    def _match_score(obs_boxes, obs_scores, obs_cls, box, cls):
        """LightTracker returns no score — borrow the closest same-class observation's."""
        cand = np.where(obs_cls == int(cls))[0]
        if len(cand) == 0:
            return float(np.min(obs_scores)) if len(obs_scores) else 0.0
        cen = obs_boxes[cand, :2] + (obs_boxes[cand, 2:] - obs_boxes[cand, :2]) / 2.0
        target = np.asarray(box[:2]) + (np.asarray(box[2:]) - np.asarray(box[:2])) / 2.0
        return float(obs_scores[cand[int(np.argmin(np.linalg.norm(cen - target, axis=1)))]])

    def _array_msg(self, header, boxes, scores, cls_ids, names, ids):
        m = DetectionArray(header=header)
        for i in range(len(boxes)):
            x1, y1, x2, y2 = boxes[i]
            d = Detection()
            d.class_id = int(cls_ids[i])
            d.class_name = names.get(int(cls_ids[i]), str(int(cls_ids[i])))
            d.score = float(scores[i])
            d.id = "" if ids is None else str(int(ids[i]))
            d.bbox.center.position.x = float((x1 + x2) / 2.0)
            d.bbox.center.position.y = float((y1 + y2) / 2.0)
            d.bbox.center.theta = 0.0
            d.bbox.size.x = float(x2 - x1)
            d.bbox.size.y = float(y2 - y1)
            m.detections.append(d)
        return m

    def _image_msg(self, header, bgr):
        m = Image(header=header, encoding="bgr8", is_bigendian=0,
                  height=bgr.shape[0], width=bgr.shape[1], step=bgr.shape[1] * 3)
        m.data = bgr.tobytes()
        return m

    def destroy_node(self):
        self._stop.set()
        self.slot.notify()
        if self._worker.is_alive():
            self._worker.join(timeout=2.0)
        super().destroy_node()


def main():
    from r2d2_vision import perf_policy
    perf_policy.apply()  # MUST precede cv2/ncnn imports (libgomp reads env at dlopen)
    rclpy.init()
    node = None
    try:
        node = DetectionNode()
        rclpy.spin(node)
    except (KeyboardInterrupt, rclpy.executors.ExternalShutdownException):
        pass
    finally:
        if node is not None:
            node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()
