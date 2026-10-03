#!/usr/bin/env python3
"""Neo-R2D2 bringup: motor node + LDROBOT lidar + hector SLAM.

    ros2 launch r2d2_bringup r2d2_bringup.launch.py
    ros2 launch r2d2_bringup r2d2_bringup.launch.py mock:=true          # UART 없이 구동 확인
    ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_lidar:=false
    ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_product:=LDLiDAR_LD14P

Components (all individually switchable):
* r2d2_motor  — /cmd_vel -> MCU move (UART owned by this node; the r2d2 app
                must not run). Port/baud come from r2d2/config_local.json.
* ldlidar     — ld19.launch.py include; publishes `scan` + base_link->base_laser TF.
* hector_mapping — scan-only SLAM, config/hector_slam.yaml.
"""
import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription
from launch.conditions import IfCondition
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node


def generate_launch_description():
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("ldlidar_ros2")
    bringup_share = get_package_share_directory("r2d2_bringup")

    # --- launch arguments ----------------------------------------------------
    args = [
        DeclareLaunchArgument("enable_motor", default_value="true"),
        DeclareLaunchArgument("enable_lidar", default_value="true"),
        DeclareLaunchArgument("enable_slam", default_value="true"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="motor node: log UART frames instead of hardware"),
        DeclareLaunchArgument("lidar_port", default_value="/dev/ttyAMA1"),
        DeclareLaunchArgument("lidar_product", default_value="LDLiDAR_LD19",
                              description="LDLiDAR_LD19 or LDLiDAR_LD14P"),
        DeclareLaunchArgument("lidar_baudrate", default_value="230400"),
        DeclareLaunchArgument("hector_params",
                              default_value=os.path.join(bringup_share, "config", "hector_slam.yaml")),
    ]

    # --- motor: /cmd_vel -> MCU move ------------------------------------------
    motor = IncludeLaunchDescription(
        PythonLaunchDescriptionSource(os.path.join(motor_share, "launch", "motor.launch.py")),
        launch_arguments={"mock": LaunchConfiguration("mock")}.items(),
        condition=IfCondition(LaunchConfiguration("enable_motor")),
    )

    # --- lidar: scan + base_link->base_laser static TF --------------------------
    lidar = IncludeLaunchDescription(
        PythonLaunchDescriptionSource(os.path.join(lidar_share, "launch", "ld19.launch.py")),
        launch_arguments={
            "port_name": LaunchConfiguration("lidar_port"),
            "product_name": LaunchConfiguration("lidar_product"),
            "serial_baudrate": LaunchConfiguration("lidar_baudrate"),
        }.items(),
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )

    # --- hector SLAM -------------------------------------------------------------
    slam = Node(
        package="hector_mapping",
        executable="hector_mapping_node",
        name="hector_slam_map",
        output="screen",
        parameters=[LaunchConfiguration("hector_params")],
        condition=IfCondition(LaunchConfiguration("enable_slam")),
    )

    return LaunchDescription(args + [motor, lidar, slam])
