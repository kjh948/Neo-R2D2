#!/usr/bin/env python3
"""Headless /image_raw source: loop an mp4 into the topic at a fixed fps.

    python3 <share>/scripts/play_video.py [video] [--fps 30] [--topic /image_raw]

(default video: /home/kjh948/workspace/yolo/output.mp4). Used to verify the 30
fps capture / 15 fps inference split without a physical camera.)
"""
import argparse
import sys
import time

import cv2
import numpy as np
import rclpy
from rclpy.node import Node
from sensor_msgs.msg import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video", nargs="?", default="/home/kjh948/workspace/yolo/output.mp4")
    ap.add_argument("--fps", type=float, default=30.0)
    ap.add_argument("--topic", default="/image_raw")
    args = ap.parse_args()

    cap = cv2.VideoCapture(args.video)
    if not cap.isOpened():
        sys.exit(f"cannot open {args.video}")

    rclpy.init()
    node = Node("play_video")
    pub = node.create_publisher(Image, args.topic, 10)
    period = 1.0 / args.fps
    next_t = time.monotonic()
    try:
        while rclpy.ok():
            ok, frame = cap.read()
            if not ok:
                cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                continue
            msg = Image()
            msg.header.frame_id = "cam"
            msg.header.stamp = node.get_clock().now().to_msg()
            msg.height, msg.width = frame.shape[:2]
            msg.encoding = "bgr8"
            msg.is_bigendian = 0
            msg.step = msg.width * 3
            msg.data = np.ascontiguousarray(frame).tobytes()
            pub.publish(msg)
            rclpy.spin_once(node, timeout_sec=0.0)
            next_t += period
            delay = next_t - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            else:
                next_t = time.monotonic()
    except KeyboardInterrupt:
        pass
    finally:
        cap.release()
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


if __name__ == "__main__":
    main()
