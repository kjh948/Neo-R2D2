"""Launch the R2-D2 motor node.

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
        DeclareLaunchArgument("serial_port", default_value="/dev/ttyS2",
                              description="MCU UART device (the r2d2 app must not be running)"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="Log UART frames instead of touching hardware"),
        DeclareLaunchArgument("cmd_vel_topic", default_value="/cmd_vel"),
        DeclareLaunchArgument("max_linear_velocity", default_value="0.5",
                              description="linear.x (m/s) that maps to power=100"),
        DeclareLaunchArgument("max_angular_velocity", default_value="1.0",
                              description="angular.z (rad/s) that maps to power=100"),
        DeclareLaunchArgument("cmd_vel_timeout", default_value="0.5",
                              description="Seconds without a Twist before an automatic stop"),
        DeclareLaunchArgument("repeat_period", default_value="0.3",
                              description="Seconds between repeated move frames while driving"),
        DeclareLaunchArgument("invert_strafe", default_value="false"),
        Node(
            package="r2d2_motor",
            executable="motor_node",
            name="r2d2_motor",
            output="screen",
            parameters=[{
                "serial_port": ParameterValue(LaunchConfiguration("serial_port"), value_type=str),
                "mock": ParameterValue(LaunchConfiguration("mock"), value_type=bool),
                "cmd_vel_topic": ParameterValue(LaunchConfiguration("cmd_vel_topic"), value_type=str),
                "max_linear_velocity": ParameterValue(LaunchConfiguration("max_linear_velocity"), value_type=float),
                "max_angular_velocity": ParameterValue(LaunchConfiguration("max_angular_velocity"), value_type=float),
                "cmd_vel_timeout": ParameterValue(LaunchConfiguration("cmd_vel_timeout"), value_type=float),
                "repeat_period": ParameterValue(LaunchConfiguration("repeat_period"), value_type=float),
                "invert_strafe": ParameterValue(LaunchConfiguration("invert_strafe"), value_type=bool),
            }],
        ),
    ])
