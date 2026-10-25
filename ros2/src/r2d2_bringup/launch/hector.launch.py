#!/usr/bin/env python3
"""Neo-R2D2 bringup with hector_slam: motor + COIN lidar + hector_mapping (no rf2o).

Sibling of r2d2_bringup.launch.py (which runs slam_toolbox + rf2o) — same lidar
conventions, but hector does PURE SCAN-MATCHING SLAM: no odometry node, no
odom frame. hector publishes map->base_link directly from the scans.

    ros2 launch r2d2_bringup hector.launch.py
    ros2 launch r2d2_bringup hector.launch.py mock:=true          # UART 없이 구동 확인
    ros2 launch r2d2_bringup hector.launch.py enable_lidar:=false
    ros2 launch r2d2_bringup hector.launch.py lidar_version:=3    # coin_d4_driver version param
    ros2 launch r2d2_bringup hector.launch.py enable_imu:=true imu_device:=/dev/i2c-1

Components (all individually switchable):
* r2d2_motor  — /cmd_vel -> MCU move (UART owned by this node; the r2d2 app
                must not run). Port/baud come from r2d2/config_local.json.
* coin_d4_driver — single_coin_d4_node publishes `scan`; the base_link->base_laser
                static TF is started here (the driver itself publishes no TF).
                Upside-down mount convention (hardware-verified 2026-10-24):
                roll=pi (base_laser Y/Z opposite base_link), lidar_reverse stays
                false — the driver's angle/range pairing cancels the mirror in
                the DATA, and consumers that respect the TF (slam_toolbox,
                RViz, and now hector — see its local patch note below) all land
                in the same convention. Only remount geometry changes need
                laser_tf_x/y/z/roll.
* hector_mapping — PURE scan-matching SLAM, config/hector_slam.yaml.
                TF chain: map ->(hector)-> base_link ->(static)-> base_laser.
                LOCAL PATCH (2026-10-25): this ROS2 port used to IGNORE the
                base<-laser TF when building its scan (laser_transform lookup
                left unused after the point-cloud path was dropped), so the
                matched scan was a mirrored/origin-shifted copy of what RViz
                and slam_toolbox see — the /scan broke away from the /map
                under every yaw. HectorMappingRos.cpp now transforms every
                scan point through the TF (rosLaserScanToDataContainerTf).
                NO rf2o here: odom_frame == base_frame in the yaml, and an
                rf2o session left over from the slam_toolbox bringup would
                multi-parent base_link (odom->base_link vs map->base_link) —
                the stale-session guard below refuses that combination.
"""
import math
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
    over /dev/ttyAMA1. Also guards against running hector and slam_toolbox
    at the same time (both would publish map->odom).
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
            "terminal, verify with `pgrep -af 'hector_mapping|slam_toolbox|rf2o_laser_odometry|single_coin_d4|motor_node'`, "
            "pkill the leftovers, and restart RViz too (its Map display keeps "
            "the last latched /map with no timeout).")


def generate_launch_description():
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("coin_d4_driver")
    bringup_share = get_package_share_directory("r2d2_bringup")
    _guard_against_stale_session(
        ["hector_mapping", "slam_toolbox", "rf2o_laser_odometry",
         "lidar_node", "r2d2_motor"])
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
        DeclareLaunchArgument("lidar_reverse", default_value="false",
                              description="coin_d4_driver scan DATA mirror (see lidar node "
                                          "comment — false is correct for the current "
                                          "upside-down mount; true mirrors the scan and "
                                          "flips hector's motion estimate)"),
        DeclareLaunchArgument("laser_tf_x", default_value="0.18",
                              description="base_laser FORWARD offset from base_link [m] (measured)"),
        DeclareLaunchArgument("laser_tf_z", default_value="0.083",
                              description="base_laser height above base_link [m] (measured 8.3 cm)"),
        DeclareLaunchArgument("laser_tf_roll", default_value="0.0",
                              description="keep 0 for hector: with lidar_reverse:=false the COIN "
                                          "driver's angle/range pairing already cancels the "
                                          "upside-down mirror in the scan DATA, so bins are "
                                          "robot-view angles. The patched hector and RViz now "
                                          "apply the SAME TF to scans, so scan/map stay aligned "
                                          "for any roll — but roll:=pi additionally MIRRORS the "
                                          "world (map and robot arrow turn opposite to physical). "
                                          "0 = map keeps the true left/right of the room"),
        DeclareLaunchArgument("laser_tf_yaw", default_value="0.0",
                              description="heading offset (rad) of base_laser vs base_link in the "
                                          "static TF. Set 3.14159 IF the walk test shows the map "
                                          "rotated 180 deg (robot arrow pointing backwards while "
                                          "driving forward) — that is the encoder-zero offset "
                                          "signature, not a mount error"),
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
                "frame_id": "base_laser",  # keep hector/nav2 sensor_frame consistent
                # Default false: the driver's parse_lidar_serial_data() already pairs
                # angle with the reversed sample index, which cancels the upside-down
                # mount mirror. Setting reverse:=true double-mirrors the scan and made
                # rf2o estimate REVERSED odometry (hardware-verified 2026-10-24:
                # forward drive -> odom_x negative while front wall distance shrank).
                # Use true only if you remount the lidar right-side-up OR pair it with
                # laser_tf_roll:=pi.
                "reverse": ParameterValue(LaunchConfiguration("lidar_reverse"), value_type=bool),
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
            "--yaw", LaunchConfiguration("laser_tf_yaw"),
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

    # --- hector SLAM (pure scan matching, no odometry node) -------------------------
    slam = Node(
        package="hector_mapping",
        executable="hector_mapping_node",
        name="hector_mapping",
        output="screen",
        parameters=[LaunchConfiguration("hector_params")],
        condition=IfCondition(LaunchConfiguration("enable_slam")),
    )

    components = [motor, lidar, laser_tf]
    if imu_share:
        components.append(imu)
    else:
        components.append(LogInfo(
            condition=IfCondition(LaunchConfiguration("enable_imu")),
            msg="[r2d2_bringup] ros2_mpu6050 is not built; colcon build it to enable the IMU"))
    components.append(slam)
    return LaunchDescription(args + components)
