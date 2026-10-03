#!/usr/bin/env python3
"""Neo-R2D2 bringup: motor node + LDROBOT lidar + hector SLAM.

    ros2 launch r2d2_bringup r2d2_bringup.launch.py
    ros2 launch r2d2_bringup r2d2_bringup.launch.py mock:=true          # UART 없이 구동 확인
    ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_lidar:=false
    ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_product:=LDLiDAR_LD14P
    ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_imu:=true imu_device:=/dev/i2c-1

Components (all individually switchable):
* r2d2_motor  — /cmd_vel -> MCU move (UART owned by this node; the r2d2 app
                must not run). Port/baud come from r2d2/config_local.json.
* ldlidar     — ld19.launch.py include; publishes `scan` + base_link->base_laser TF.
* hector_mapping — scan-only SLAM, config/hector_slam.yaml.
"""
import os

from ament_index_python import PackageNotFoundError
from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription, LogInfo
from launch.conditions import IfCondition
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description():
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("ldlidar_ros2")
    bringup_share = get_package_share_directory("r2d2_bringup")
    try:
        imu_share = get_package_share_directory("ros2_mpu6050")
    except PackageNotFoundError:  # driver not built in this workspace
        imu_share = ""

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
        DeclareLaunchArgument("enable_imu", default_value="false",
                              description="start the MPU6050 driver (publishes imu/mpu6050)"),
        DeclareLaunchArgument("imu_device", default_value="/dev/i2c-1",
                              description="MPU6050 I2C device"),
        DeclareLaunchArgument("imu_frame", default_value="base_link",
                              description="frame_id of the Imu messages"),
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

    # --- MPU6050 IMU over I2C ------------------------------------------------------
    imu_params = [{
        "device": LaunchConfiguration("imu_device"),
        "imu_frame": LaunchConfiguration("imu_frame"),
    }]
    if imu_share:
        imu_params.insert(0, os.path.join(imu_share, "config", "params.yaml"))
    imu = Node(
        package="ros2_mpu6050",
        executable="ros2_mpu6050",
        name="mpu6050_sensor",
        output="screen",
        parameters=imu_params,
        condition=IfCondition(LaunchConfiguration("enable_imu")),
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

    components = [motor, lidar]
    if imu_share:
        components.append(imu)
    else:
        components.append(LogInfo(
            condition=IfCondition(LaunchConfiguration("enable_imu")),
            msg="[r2d2_bringup] ros2_mpu6050 is not built; colcon build it to enable the IMU"))
    components.append(slam)
    return LaunchDescription(args + components)
