"""Launch the R2-D2 motor node.

Defaults for the UART link come from the r2d2 app config (r2d2/config_local.json,
then ~/.config/r2d2/config.json / /etc/r2d2/config.json); empty launch args mean
"use the config file".

    ros2 launch r2d2_motor motor.launch.py
    ros2 launch r2d2_motor motor.launch.py mock:=true
    ros2 launch r2d2_motor motor.launch.py serial_port:=/dev/ttyUSB0 invert_strafe:=true
"""
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.parameter_descriptions import ParameterValue
from launch_ros.actions import Node


def generate_launch_description() -> LaunchDescription:
    return LaunchDescription([
        DeclareLaunchArgument("config_file", default_value="",
                              description="r2d2 JSON config; empty = auto-detect config_local.json"),
        DeclareLaunchArgument("serial_port", default_value="",
                              description="MCU UART device; empty = from config file "
                                          "(the r2d2 app must not be running)"),
        DeclareLaunchArgument("baudrate", default_value="-1",
                              description="UART baudrate; -1 = from config file"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="Log UART frames instead of touching hardware"),
        DeclareLaunchArgument("cmd_vel_topic", default_value="/cmd_vel"),
        DeclareLaunchArgument("max_linear_velocity", default_value="0.17",
                              description="linear.x (m/s) that maps to power=100 — "
                                          "measured top speed at full power is 0.167 m/s "
                                          "(bench 2026-10-26, 1 m in 6 s)"),
        DeclareLaunchArgument("max_angular_velocity", default_value="1.0",
                              description="angular.z (rad/s) that maps to power=100 "
                                          "(4-direction 'differential' teleop mode only — "
                                          "'tank' mode uses wheel_separation instead)"),
        DeclareLaunchArgument("wheel_separation", default_value="0.235",
                              description="measured track width [m] (reference; the 'tank' "
                                          "polar mapping uses forward_gain/rotation_gain)"),
        DeclareLaunchArgument("forward_gain", default_value="0.001525",
                              description="bench-calibrated: v [m/s] = forward_gain*power (+0.0145)"),
        DeclareLaunchArgument("rotation_gain", default_value="0.00797",
                              description="bench-calibrated: in-place omega [rad/s] = rotation_gain*power"),
        DeclareLaunchArgument("cmd_vel_timeout", default_value="0.5",
                              description="Seconds without a Twist before an automatic stop"),
        DeclareLaunchArgument("repeat_period", default_value="0.3",
                              description="Seconds between repeated move frames while driving"),
        DeclareLaunchArgument("invert_strafe", default_value="true",
                              description="Hardware truth (2026-10-18): +angular.z made the "
                                          "robot turn RIGHT — the MCU polar angle sign is "
                                          "mirrored vs the ROS convention, so flip wz by default"),
        DeclareLaunchArgument("mapping_mode", default_value="differential",
                              description="'differential' (teleop 4-dir), 'holonomic' (vx/vy) "
                                          "or 'tank' (nav2 vx/wz -> MCU polar — what "
                                          "r2d2_navigation uses)"),
        DeclareLaunchArgument("gin_period", default_value="5.0",
                              description="Seconds between MCU gin status polls (0 = no polling); "
                                          "each reply republishes /r2d2/status"),
        DeclareLaunchArgument("allow_power_off", default_value="false",
                              description="Gate for /r2d2/power_off (MCU shut-down frame)"),
        DeclareLaunchArgument("sound_enabled", default_value="true",
                              description="Bridge -> r2d2_sound routing (projector auto-off "
                                          "sequence, MCU play_sound frames)"),
        DeclareLaunchArgument("status_transient", default_value="true",
                              description="transient_local /r2d2/status (late subscribers get "
                                          "the last known MCU state immediately)"),
        DeclareLaunchArgument("mock_responds", default_value="true",
                              description="mock only: canned MCU ready/gin replies so "
                                          "/r2d2/status flows without hardware"),
        Node(
            package="r2d2_motor",
            executable="motor_node",
            name="r2d2_motor",
            output="screen",
            parameters=[{
                "config_file": ParameterValue(LaunchConfiguration("config_file"), value_type=str),
                "serial_port": ParameterValue(LaunchConfiguration("serial_port"), value_type=str),
                "baudrate": ParameterValue(LaunchConfiguration("baudrate"), value_type=int),
                "mock": ParameterValue(LaunchConfiguration("mock"), value_type=bool),
                "cmd_vel_topic": ParameterValue(LaunchConfiguration("cmd_vel_topic"), value_type=str),
                "max_linear_velocity": ParameterValue(LaunchConfiguration("max_linear_velocity"), value_type=float),
                "max_angular_velocity": ParameterValue(LaunchConfiguration("max_angular_velocity"), value_type=float),
                "cmd_vel_timeout": ParameterValue(LaunchConfiguration("cmd_vel_timeout"), value_type=float),
                "repeat_period": ParameterValue(LaunchConfiguration("repeat_period"), value_type=float),
                "invert_strafe": ParameterValue(LaunchConfiguration("invert_strafe"), value_type=bool),
                "wheel_separation": ParameterValue(LaunchConfiguration("wheel_separation"), value_type=float),
                "forward_gain": ParameterValue(LaunchConfiguration("forward_gain"), value_type=float),
                "rotation_gain": ParameterValue(LaunchConfiguration("rotation_gain"), value_type=float),
                "mapping_mode": ParameterValue(LaunchConfiguration("mapping_mode"), value_type=str),
            }],
        ),
    ])
