#!/usr/bin/env python3
"""Detection session: v4l2_camera_node (30 fps) + r2d2_vision detection_node (15 fps NCNN).

Independent of r2d2_bringup.launch.py — start/stop it freely alongside slam/nav.
Outputs (ns `yolo`): yolo/detections, yolo/tracking, yolo/debug_image.

    ros2 launch r2d2_vision vision.launch.py
    ros2 launch r2d2_vision vision.launch.py use_camera:=false   # feed /image_raw externally
    ros2 launch r2d2_vision vision.launch.py model_path:=/home/kjh948/workspace/yolo/yolo26n_ncnn_model

Camera is NOT remapped: it publishes /image_raw at root, the node subscribes the
absolute topic while living in the `yolo` namespace.
"""

import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.conditions import IfCondition
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description():
    share = get_package_share_directory("r2d2_vision")
    default_model = os.path.join(os.environ.get("NEO_YOLO_MODEL_HOME",
                                                "/home/kjh948/workspace/yolo"),
                                 "yoloe-26n-det_ncnn_model")

    args = [
        DeclareLaunchArgument("use_camera", default_value="true",
                              description="start v4l2_camera_node (false = external /image_raw)"),
        DeclareLaunchArgument("video_device", default_value="/dev/video0"),
        DeclareLaunchArgument("camera_width", default_value="640"),
        DeclareLaunchArgument("camera_height", default_value="480"),
        DeclareLaunchArgument("pixel_format", default_value="",
                              description="v4l2 fourcc, e.g. MJPG for 640x480@30 on weak USB cams"),
        DeclareLaunchArgument("ns", default_value="yolo", description="detection node namespace"),
        DeclareLaunchArgument("model_path", default_value=default_model),
        DeclareLaunchArgument("max_fps", default_value="15.0"),
        DeclareLaunchArgument("threshold", default_value="0.4"),
        DeclareLaunchArgument("use_tracking", default_value="true"),
        DeclareLaunchArgument("tracker_type", default_value="bytetrack"),
        DeclareLaunchArgument("debug_image", default_value="true"),
        DeclareLaunchArgument("ncnn_threads", default_value="2"),
        DeclareLaunchArgument("image_reliability", default_value="1",
                              description="0 best_effort / 1 reliable / 2 system — must match "
                                          "the camera publisher; mismatch = silently no frames"),
        # Remaining knobs (iou, class_filter, track_* thresholds, image_topic) are node
        # parameters — pass them with the standard --ros-args -p syntax on ros2 run, or
        # copy config/vision_params.yaml and append `--params-file` style YAML yourself.
    ]

    camera = Node(
        package="v4l2_camera", executable="v4l2_camera_node", name="v4l2_camera",
        output="screen", condition=IfCondition(LaunchConfiguration("use_camera")),
        parameters=[{
            "video_device": LaunchConfiguration("video_device"),
            "image_size": [ParameterValue(LaunchConfiguration("camera_width"), value_type=int),
                           ParameterValue(LaunchConfiguration("camera_height"), value_type=int)],
            "pixel_format": ParameterValue(LaunchConfiguration("pixel_format"), value_type=str),
            "frame_id": "cam",
        }])

    detection = Node(
        package="r2d2_vision", executable="detection_node", name="detection_node",
        namespace=LaunchConfiguration("ns"), output="screen",
        parameters=[{
            "model_path": LaunchConfiguration("model_path"),
            "max_fps": ParameterValue(LaunchConfiguration("max_fps"), value_type=float),
            "threshold": ParameterValue(LaunchConfiguration("threshold"), value_type=float),
            "use_tracking": ParameterValue(LaunchConfiguration("use_tracking"), value_type=bool),
            "tracker_type": LaunchConfiguration("tracker_type"),
            "debug_image": ParameterValue(LaunchConfiguration("debug_image"), value_type=bool),
            "ncnn_threads": ParameterValue(LaunchConfiguration("ncnn_threads"), value_type=int),
            "image_reliability": ParameterValue(LaunchConfiguration("image_reliability"),
                                                value_type=int),
            "image_topic": "/image_raw",
        }])

    return LaunchDescription(args + [camera, detection])
