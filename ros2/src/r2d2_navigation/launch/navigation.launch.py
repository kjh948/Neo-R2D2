#!/usr/bin/env python3
"""Neo-R2D2 navigation: sensors + RF2O/IMU EKF odometry + localization + Nav2.

    # slam_toolbox mode (default): localize against a serialized posegraph
    ros2 launch r2d2_navigation navigation.launch.py posegraph:=<abs>/maps/r2d2_home_posegraph
    # hector mode: hector has NO posegraph — localize against a pgm/yaml saved
    # from a hector session with map_saver (save_map.sh SKIP_POSEGRAPH=1)
    ros2 launch r2d2_navigation navigation.launch.py \
        localization:=map_server map_yaml:=<abs>/maps/hector_home.yaml
    ros2 launch r2d2_navigation navigation.launch.py mock:=true enable_nav2:=false
    (create/refresh posegraph maps with: ros2 run r2d2_navigation save_map.sh
     during a bringup mapping session — writes maps/<name>_posegraph.*;
     for hector sessions save only pgm/yaml: SKIP_POSEGRAPH=1 save_map.sh)

Architecture (differs from linorobot2 deliberately):
  * r2d2_motor runs with mapping_mode:=tank (nav2 Twist (vx, wz) -> MCU
    polar power/angle, the operator-confirmed differential kinematics)
  * NO wheel odom: rf2o publishes raw /odom_rf2o; robot_localization fuses
    RF2O and (optionally) MPU6050 gyro z to publish /odom + odom->base_link.
  * localization:=toolbox  — slam_toolbox (mode: localization) localizes the
    serialized posegraph and owns map->odom; it publishes /map itself.
  * localization:=map_server — map_server loads the saved pgm and AMCL owns
    map->odom (the classic stack, required for hector-mapped pgm files, since
    hector cannot serialize or reload a map). AMCL params live in
    nav2_params.yaml.
  * Re-localization: RViz 2D Pose Estimate publishes /initialpose (handled
    natively by both engines).

nav2 core (planner/controller/bt/smoother/lifecycle) starts only when Nav2 is
installed; enable_nav2:=false brings up motor + sensors + fused odometry +
localization.
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
from launch.substitutions import LaunchConfiguration, PythonExpression
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue
from nav2_common.launch import RewrittenYaml


def _guard_against_stale_session(node_names):
    """Refuse to start while a previous bringup/navigation session is alive
    (duplicate slam_toolbox keeps latching a stale /map, lidar_node steals
    ttyAMA1 — same failure as documented in r2d2_bringup.launch.py, 2026-10-18)."""
    try:
        out = subprocess.run(["ros2", "node", "list"],
                             capture_output=True, text=True, timeout=15).stdout
    except Exception:
        return  # best-effort: never block a normal start on its own failure
    stale = [n for n in node_names if f"/{n}" in out]
    if stale:
        raise RuntimeError(
            "[r2d2_navigation] refusing to start: nodes already running from a "
            f"previous session ({', '.join(stale)}). Ctrl+C that session's "
            "terminal, verify with `pgrep -af 'slam_toolbox|rf2o_laser_odometry|single_coin_d4|motor_node'`, "
            "pkill the leftovers, and restart RViz too.")


def generate_launch_description():
    nav_share = get_package_share_directory("r2d2_navigation")
    bringup_share = get_package_share_directory("r2d2_bringup")
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("coin_d4_driver")
    try:
        imu_share = get_package_share_directory("ros2_mpu6050")
    except PackageNotFoundError:
        imu_share = ""
    imu_enabled = PythonExpression([
        "'", LaunchConfiguration("enable_imu"), "' == 'true' and '",
        str(bool(imu_share)).lower(), "' == 'true'"])
    ekf_imu_enabled = PythonExpression([
        "'", LaunchConfiguration("enable_slam_loc"), "' == 'true' and '",
        LaunchConfiguration("enable_imu"), "' == 'true' and '",
        str(bool(imu_share)).lower(), "' == 'true'"])
    ekf_no_imu_enabled = PythonExpression([
        "'", LaunchConfiguration("enable_slam_loc"), "' == 'true' and ('",
        LaunchConfiguration("enable_imu"), "' != 'true' or '",
        str(bool(imu_share)).lower(), "' != 'true')"])
    # hector_mapping too: a leftover hector session would publish map->base_link
    # while the EKF owns odom->base_link here -> base_link gets two TF parents.
    _guard_against_stale_session(
        ["lidar_node", "r2d2_motor", "slam_toolbox", "rf2o_laser_odometry",
         "ekf_filter_node", "mpu6050_sensor", "hector_mapping"])

    default_posegraph = os.path.join(nav_share, "maps", "r2d2_home_posegraph")

    # localization:=toolbox|map_server — one engine at a time, both behind the
    # same enable_slam_loc switch (the EKF feeds odom->base_link in either mode).
    cond_toolbox = IfCondition(PythonExpression([
        "'", LaunchConfiguration("enable_slam_loc"), "' == 'true' and '",
        LaunchConfiguration("localization"), "' == 'toolbox'"]))
    cond_map_server = IfCondition(PythonExpression([
        "'", LaunchConfiguration("enable_slam_loc"), "' == 'true' and '",
        LaunchConfiguration("localization"), "' == 'map_server'"]))

    args = [
        DeclareLaunchArgument("localization", default_value="toolbox",
                              description="'toolbox': slam_toolbox localization against a "
                                          "posegraph (default). 'map_server': map_server + AMCL "
                                          "against a pgm/yaml map — the hector workflow, hector "
                                          "has no posegraph to serialize."),
        DeclareLaunchArgument("posegraph", default_value=default_posegraph,
                              description="serialized slam_toolbox posegraph (path WITHOUT the "
                                          ".posegraph/.data extensions), from save_map.sh"),
        DeclareLaunchArgument("map_yaml",
                              default_value=os.path.join(nav_share, "maps", "r2d2_home.yaml"),
                              description="pgm/yaml map for localization:=map_server (the .yaml "
                                          "saved by save_map.sh SKIP_POSEGRAPH=1 / map_saver_cli)"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="motor node mock UART"),
        DeclareLaunchArgument("enable_lidar", default_value="true"),
        DeclareLaunchArgument("lidar_port", default_value="/dev/ttyAMA1"),
        DeclareLaunchArgument("lidar_reverse", default_value="false",
                              description="coin_d4_driver scan DATA mirror — false is correct "
                                          "for the current upside-down mount (true double-"
                                          "mirrors and reverses rf2o odom; see r2d2_bringup)"),
        DeclareLaunchArgument("enable_slam_loc", default_value="true",
                              description="RF2O + EKF odometry and the selected localization "
                                          "engine (map->odom TF); keep true on hardware"),
        DeclareLaunchArgument("enable_imu", default_value="true",
                              description="fuse MPU6050 gyro z in the EKF; false runs "
                                          "RF2O-only EKF"),
        DeclareLaunchArgument("imu_device", default_value="/dev/i2c-1",
                              description="MPU6050 I2C device"),
        DeclareLaunchArgument("imu_frame", default_value="base_link",
                              description="frame_id of the IMU messages"),
        DeclareLaunchArgument("enable_nav2", default_value="true",
                              description="nav2 servers (requires ros-jazzy-navigation2)"),
        DeclareLaunchArgument("controller", default_value="rpp",
                              description="Nav2 path controller: rpp (default) or mppi",
                              choices=["rpp", "mppi"]),
        DeclareLaunchArgument("params_file",
                              default_value=os.path.join(nav_share, "config", "nav2_params.yaml")),
        DeclareLaunchArgument("use_sim_time", default_value="false"),
    ]

    # --- sensors & base ---------------------------------------------------------
    motor = IncludeLaunchDescription(
        PythonLaunchDescriptionSource(os.path.join(motor_share, "launch", "motor.launch.py")),
        launch_arguments={
            "mock": LaunchConfiguration("mock"),
            "mapping_mode": "tank",
        }.items(),
    )

    # coin_d4_driver publishes no TF; the base_link->base_laser static TF is ours.
    # Measured mount (2026-10-24): 18 cm FORWARD of base_link, 8.3 cm high.
    # The COIN-D3 sits upside down, but the driver's angle/range pairing already
    # cancels the mirror in the scan DATA — keep reverse: false. A second data
    # mirror (reverse: true) left/right-flipped the scan and made rf2o estimate
    # REVERSED odometry (hardware-verified 2026-10-24). base_laser stays PARALLEL
    # to base_link (identity rotation — same convention as r2d2_bringup).
    lidar = Node(
        package="coin_d4_driver",
        executable="single_coin_d4_node",
        name="lidar_node",
        output="screen",
        parameters=[
            os.path.join(lidar_share, "params", "single_lidar_node.yaml"),
            {
                "port": LaunchConfiguration("lidar_port"),
                "frame_id": "base_laser",  # matches nav2_params sensor_frame / rf2o
                # false is correct for the current upside-down mount (double mirror
                # flips rf2o odom direction — see r2d2_bringup lidar node comment)
                "reverse": ParameterValue(LaunchConfiguration("lidar_reverse"), value_type=bool),
            },
        ],
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )
    laser_tf = Node(
        package="tf2_ros",
        executable="static_transform_publisher",
        name="base_link_to_base_laser",
        # --roll pi: physically true for the upside-down mount (pairs with
        # lidar_reverse:=false; see r2d2_bringup). rf2o only uses the yaw part
        # of this TF, so odometry direction is unaffected.
        arguments=["--x", "0.18", "--y", "0", "--z", "0.083", "--roll", str(math.pi),
                   "--frame-id", "base_link", "--child-frame-id", "base_laser"],
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )

    # --- raw RF2O odometry (EKF is the sole odom TF publisher) ---------------------
    rf2o = Node(
        package="rf2o_laser_odometry",
        executable="rf2o_laser_odometry_node",
        name="rf2o_laser_odometry",
        output="screen",
        parameters=[{
            "laser_scan_topic": "/scan",
            "odom_topic": "/odom_rf2o",
            "base_frame_id": "base_link",
            "odom_frame_id": "odom",
            "publish_tf": False,
            "init_pose_from_topic": "",  # upstream default /base_pose_ground_truth!
            "freq": 20.0,
        }],
        condition=IfCondition(LaunchConfiguration("enable_slam_loc")),
    )

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
        condition=IfCondition(imu_enabled),
    )

    ekf_common = os.path.join(bringup_share, "config", "ekf.yaml")
    ekf_imu = os.path.join(bringup_share, "config", "ekf_imu.yaml")
    ekf_with_imu = Node(
        package="robot_localization",
        executable="ekf_node",
        name="ekf_filter_node",
        output="screen",
        parameters=[ekf_common, ekf_imu, {
            "use_sim_time": LaunchConfiguration("use_sim_time"),
        }],
        remappings=[("odometry/filtered", "/odom")],
        condition=IfCondition(ekf_imu_enabled),
    )
    ekf_without_imu = Node(
        package="robot_localization",
        executable="ekf_node",
        name="ekf_filter_node",
        output="screen",
        parameters=[ekf_common, {
            "use_sim_time": LaunchConfiguration("use_sim_time"),
        }],
        remappings=[("odometry/filtered", "/odom")],
        condition=IfCondition(ekf_no_imu_enabled),
    )

    # Shared nav2 parameter file (used by map_server/AMCL below and nav2 core).
    params = RewrittenYaml(
        source_file=LaunchConfiguration("params_file"),
        root_key="",
        param_rewrites={
            "use_sim_time": LaunchConfiguration("use_sim_time"),
            "controller_server.ros__parameters.FollowPath.plugin": PythonExpression([
                "'nav2_regulated_pure_pursuit_controller::RegulatedPurePursuitController' "
                "if '", LaunchConfiguration("controller"),
                "' == 'rpp' else "
                "'nav2_mppi_controller::MPPIController'",
            ]),
        },
        convert_types=True,
    )

    # --- slam_toolbox localization: posegraph -> /map + map->odom TF ---------------
    slam_loc = Node(
        package="slam_toolbox",
        executable="localization_slam_toolbox_node",
        name="slam_toolbox",
        output="screen",
        parameters=[
            RewrittenYaml(
                source_file=os.path.join(nav_share, "config", "slam_toolbox_localization.yaml"),
                root_key="",
                param_rewrites={
                    "map_file_name": LaunchConfiguration("posegraph"),
                    "use_sim_time": LaunchConfiguration("use_sim_time"),
                },
                convert_types=True,
            )
        ],
        remappings=[("rosout", "/r2d2_slam_loc/rosout")],
        condition=cond_toolbox,
    )
    slam_lifecycle = Node(
        package="nav2_lifecycle_manager",
        executable="lifecycle_manager",
        name="lifecycle_manager_slam_loc",
        output="screen",
        parameters=[{
            "use_sim_time": LaunchConfiguration("use_sim_time"),
            "autostart": True,
            "node_names": ["slam_toolbox"],
        }],
        condition=cond_toolbox,
    )

    # --- map_server + AMCL: hector-pgm localization (localization:=map_server) ----
    # map_server loads <map_yaml> and publishes /map; AMCL does particle-filter
    # localization against it and owns map->odom. The dict override lands on the
    # node's own namespace, so nav2_params.yaml keeps yaml_filename empty.
    map_server = Node(
        package="nav2_map_server",
        executable="map_server",
        name="map_server",
        output="screen",
        parameters=[params, {
            "yaml_filename": LaunchConfiguration("map_yaml"),
            "use_sim_time": LaunchConfiguration("use_sim_time"),
        }],
        condition=cond_map_server,
    )
    amcl = Node(
        package="nav2_amcl",
        executable="amcl",
        name="amcl",
        output="screen",
        parameters=[params, {
            "use_sim_time": LaunchConfiguration("use_sim_time"),
        }],
        condition=cond_map_server,
    )
    map_lifecycle = Node(
        package="nav2_lifecycle_manager",
        executable="lifecycle_manager",
        name="lifecycle_manager_map_loc",
        output="screen",
        parameters=[{
            "use_sim_time": LaunchConfiguration("use_sim_time"),
            "autostart": True,
            "node_names": ["map_server", "amcl"],
        }],
        condition=cond_map_server,
    )

    # --- nav2 core ---------------------------------------------------------------
    common = {"output": "screen", "parameters": [params],
              "condition": IfCondition(LaunchConfiguration("enable_nav2"))}

    planner = Node(package="nav2_planner", executable="planner_server",
                   name="planner_server", **common)
    controller = Node(package="nav2_controller", executable="controller_server",
                      name="controller_server", remappings=[("cmd_vel", "cmd_vel_nav")], **common)
    smoother = Node(package="nav2_smoother", executable="smoother_server",
                    name="smoother_server", **common)
    bt_navigator = Node(package="nav2_bt_navigator", executable="bt_navigator",
                        name="bt_navigator", **common)
    behavior_server = Node(package="nav2_behaviors", executable="behavior_server",
                           name="behavior_server", **common)
    velocity_smoother = Node(package="nav2_velocity_smoother", executable="velocity_smoother",
                             name="velocity_smoother",
                             remappings=[("cmd_vel", "cmd_vel_nav"),
                                         ("cmd_vel_smoothed", "cmd_vel")],
                             **common)

    lifecycle_nodes = ["planner_server", "controller_server",
                       "smoother_server", "behavior_server", "bt_navigator",
                       "velocity_smoother"]
    lifecycle_manager = Node(
        package="nav2_lifecycle_manager",
        executable="lifecycle_manager",
        name="lifecycle_manager_navigation",
        output="screen",
        parameters=[{
            "use_sim_time": LaunchConfiguration("use_sim_time"),
            "autostart": True,
            "node_names": lifecycle_nodes,
        }],
        condition=IfCondition(LaunchConfiguration("enable_nav2")),
    )

    components = [
        motor, lidar, laser_tf, rf2o, ekf_with_imu, ekf_without_imu,
        slam_loc, slam_lifecycle,
        map_server, amcl, map_lifecycle,
        planner, controller, smoother, behavior_server, bt_navigator,
        velocity_smoother, lifecycle_manager,
    ]
    if imu_share:
        components.append(imu)
    else:
        components.append(LogInfo(
            condition=IfCondition(LaunchConfiguration("enable_imu")),
            msg="[r2d2_navigation] ros2_mpu6050 is not built; EKF will run without IMU data"))
    return LaunchDescription(args + components)
