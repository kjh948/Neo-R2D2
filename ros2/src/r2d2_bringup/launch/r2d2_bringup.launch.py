#!/usr/bin/env python3
"""Neo-R2D2 bringup: motor node + COIN lidar + rf2o odometry + slam_toolbox.

    ros2 launch r2d2_bringup r2d2_bringup.launch.py
    ros2 launch r2d2_bringup r2d2_bringup.launch.py mock:=true          # UART 없이 구동 확인
    ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_lidar:=false
    ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_version:=3    # coin_d4_driver version param
    ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_imu:=true imu_device:=/dev/i2c-1

Components (all individually switchable):
* r2d2_motor  — /cmd_vel -> MCU move (UART owned by this node; the r2d2 app
                must not run). Port/baud come from r2d2/config_local.json.
* coin_d4_driver — single_coin_d4_node publishes `scan`; the base_link->base_laser
                static TF is started here (the driver itself publishes no TF).
                The sensor is mounted upside down, so that TF carries roll=pi
                (see the laser_tf_roll argument).
* rf2o_laser_odometry — /scan -> /odom + odom->base_link TF (the robot has no
*                wheel odometry; this is the only odom source).
* slam_toolbox — async SLAM, config/slam_toolbox.yaml; owns map->odom.
                TF chain: map ->(slam_toolbox)-> odom ->(rf2o)-> base_link ->(static)-> base_laser.
"""
import os
import subprocess

from ament_index_python import PackageNotFoundError
from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription, LogInfo
from launch.conditions import IfCondition
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def _guard_against_stale_session(node_names):
    """Refuse to start while a previous bringup/navigation session is alive.

    Hardware truth (2026-10-18): after a half-dead Ctrl+C, an orphan SLAM node
    kept latching its stale /map (TRANSIENT_LOCAL) alongside the new one ->
    RViz "kept showing the previous map", and two lidar_node instances fought
    over /dev/ttyAMA1. Fail fast with instructions instead.
    """
    try:
        out = subprocess.run(["ros2", "node", "list"],
                             capture_output=True, text=True, timeout=15).stdout
    except Exception:
        return  # best-effort guard: never block a normal start on its own failure
    stale = [n for n in node_names if f"/{n}" in out]
    if stale:
        raise RuntimeError(
            "[r2d2_bringup] refusing to start: nodes already running from a "
            f"previous session ({', '.join(stale)}). Ctrl+C that session's "
            "terminal, verify with `pgrep -af 'slam_toolbox|rf2o_laser_odometry|single_coin_d4|motor_node'`, "
            "pkill the leftovers, and restart RViz too (its Map display keeps "
            "the last latched /map with no timeout).")


def generate_launch_description():
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("coin_d4_driver")
    bringup_share = get_package_share_directory("r2d2_bringup")
    _guard_against_stale_session(
        ["slam_toolbox", "rf2o_laser_odometry", "lidar_node", "r2d2_motor"])
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
        DeclareLaunchArgument("lidar_version", default_value="4",
                              description="coin_d4_driver protocol version: "
                                          "1=M1C1_Mini_v1, 2=M1C1_Mini_v2, "
                                          "3=M1CT_Coin_Plus, 4=M1CT_TOF (baud derived from version)"),
        DeclareLaunchArgument("laser_tf_x", default_value="0.18",
                              description="base_laser FORWARD offset from base_link [m] (measured)"),
        DeclareLaunchArgument("laser_tf_z", default_value="0.083",
                              description="base_laser height above base_link [m] (measured 8.3 cm)"),
        DeclareLaunchArgument("laser_tf_roll", default_value="3.141592653589793",
                              description="COIN-D4 is mounted UPSIDE DOWN (top face down): "
                                          "roll pi flips the scan left/right back into place; "
                                          "set 0 if the sensor is ever remounted upright"),
        DeclareLaunchArgument("slam_params",
                              default_value=os.path.join(bringup_share, "config", "slam_toolbox.yaml")),
        DeclareLaunchArgument("rf2o_freq", default_value="20.0",
                              description="rf2o laser odometry output rate [Hz]"),
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
    lidar = Node(
        package="coin_d4_driver",
        executable="single_coin_d4_node",
        name="lidar_node",
        output="screen",
        parameters=[
            os.path.join(lidar_share, "params", "single_lidar_node.yaml"),
            {
                "port": LaunchConfiguration("lidar_port"),
                "version": ParameterValue(LaunchConfiguration("lidar_version"), value_type=int),
                "frame_id": "base_laser",  # keep slam_toolbox/rf2o/nav2 sensor_frame consistent
            },
        ],
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )
    laser_tf = Node(
        package="tf2_ros",
        executable="static_transform_publisher",
        name="base_link_to_base_laser",
        arguments=[
            "--x", LaunchConfiguration("laser_tf_x"), "--y", "0",
            "--z", LaunchConfiguration("laser_tf_z"),
            "--roll", LaunchConfiguration("laser_tf_roll"),
            "--frame-id", "base_link", "--child-frame-id", "base_laser",
        ],
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

    # --- laser odometry (rf2o): /scan -> /odom + odom->base_link TF -----------------
    rf2o = Node(
        package="rf2o_laser_odometry",
        executable="rf2o_laser_odometry_node",
        name="rf2o_laser_odometry",
        output="screen",
        parameters=[{
            "laser_scan_topic": "/scan",
            "odom_topic": "/odom",
            "base_frame_id": "base_link",
            "odom_frame_id": "odom",
            "publish_tf": True,
            "init_pose_from_topic": "",  # upstream default /base_pose_ground_truth!
            "freq": ParameterValue(LaunchConfiguration("rf2o_freq"), value_type=float),
        }],
        condition=IfCondition(LaunchConfiguration("enable_slam")),
    )

    # --- slam_toolbox (async mapping): scan+odom -> /map, map->odom TF --------------
    slam = Node(
        package="slam_toolbox",
        executable="async_slam_toolbox_node",
        name="slam_toolbox",  # keep default: services live under /slam_toolbox/*
        output="screen",
        parameters=[LaunchConfiguration("slam_params")],
        condition=IfCondition(LaunchConfiguration("enable_slam")),
    )
    slam_lifecycle = Node(
        package="nav2_lifecycle_manager",
        executable="lifecycle_manager",
        name="lifecycle_manager_slam",
        output="screen",
        parameters=[{
            "autostart": True,
            "node_names": ["slam_toolbox"],
        }],
        condition=IfCondition(LaunchConfiguration("enable_slam")),
    )

    components = [motor, lidar, laser_tf]
    if imu_share:
        components.append(imu)
    else:
        components.append(LogInfo(
            condition=IfCondition(LaunchConfiguration("enable_imu")),
            msg="[r2d2_bringup] ros2_mpu6050 is not built; colcon build it to enable the IMU"))
    components.extend([rf2o, slam, slam_lifecycle])
    return LaunchDescription(args + components)
