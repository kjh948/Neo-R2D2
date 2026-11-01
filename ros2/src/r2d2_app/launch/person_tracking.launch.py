"""Launch the person tracker; YOLO vision and motor nodes must already be running."""
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description():
    args = [
        DeclareLaunchArgument("detection_topic", default_value="/yolo/tracking"),
        DeclareLaunchArgument("head_shift_topic", default_value="/r2d2/head_shift"),
        DeclareLaunchArgument("head_center_service", default_value="/r2d2/set_head_angle"),
        DeclareLaunchArgument("status_topic", default_value="/r2d2/status"),
        DeclareLaunchArgument("cmd_vel_topic", default_value="/cmd_vel"),
        DeclareLaunchArgument("control_rate", default_value="10.0"),
        DeclareLaunchArgument("image_width", default_value="640"),
        DeclareLaunchArgument("camera_hfov_deg", default_value="80.0"),
        # centre margin in degrees: person within +/-10 deg of frame centre ->
        # head and base both hold still
        DeclareLaunchArgument("head_deadband_deg", default_value="10.0"),
        DeclareLaunchArgument("head_follow_gain", default_value="0.5"),
        DeclareLaunchArgument("max_head_shift_deg", default_value="5"),
        DeclareLaunchArgument("head_limit_deg", default_value="30.0"),
        DeclareLaunchArgument("head_recenter_deg", default_value="10.0"),
        DeclareLaunchArgument("base_rotation_speed", default_value="0.105"),
        # -1: +head angle = person out of frame to the RIGHT; ROS +wz turns the
        # robot LEFT (CCW), so rotate toward them = negative wz. Flip only if
        # hardware conventions change.
        DeclareLaunchArgument("base_rotation_sign", default_value="-1"),
        DeclareLaunchArgument("target_lost_timeout", default_value="0.7"),
    ]

    tracker = Node(
        package="r2d2_app",
        executable="person_tracker_node",
        name="person_tracker",
        output="screen",
        parameters=[{
            "detection_topic": LaunchConfiguration("detection_topic"),
            "head_shift_topic": LaunchConfiguration("head_shift_topic"),
            "head_center_service": LaunchConfiguration("head_center_service"),
            "status_topic": LaunchConfiguration("status_topic"),
            "cmd_vel_topic": LaunchConfiguration("cmd_vel_topic"),
            "control_rate": ParameterValue(LaunchConfiguration("control_rate"), value_type=float),
            "image_width": ParameterValue(LaunchConfiguration("image_width"), value_type=int),
            "camera_hfov_deg": ParameterValue(
                LaunchConfiguration("camera_hfov_deg"), value_type=float),
            "head_deadband_deg": ParameterValue(
                LaunchConfiguration("head_deadband_deg"), value_type=float),
            "head_follow_gain": ParameterValue(
                LaunchConfiguration("head_follow_gain"), value_type=float),
            "max_head_shift_deg": ParameterValue(
                LaunchConfiguration("max_head_shift_deg"), value_type=int),
            "head_limit_deg": ParameterValue(
                LaunchConfiguration("head_limit_deg"), value_type=float),
            "head_recenter_deg": ParameterValue(
                LaunchConfiguration("head_recenter_deg"), value_type=float),
            "base_rotation_speed": ParameterValue(
                LaunchConfiguration("base_rotation_speed"), value_type=float),
            "base_rotation_sign": ParameterValue(
                LaunchConfiguration("base_rotation_sign"), value_type=int),
            "target_lost_timeout": ParameterValue(
                LaunchConfiguration("target_lost_timeout"), value_type=float),
        }],
    )
    return LaunchDescription(args + [tracker])
