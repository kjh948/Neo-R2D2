#!/usr/bin/env python3
"""Neo-R2D2 navigation: sensors + rf2o odometry + slam_toolbox localization + Nav2.

    ros2 launch r2d2_navigation navigation.launch.py posegraph:=<abs>/maps/r2d2_home_posegraph
    ros2 launch r2d2_navigation navigation.launch.py mock:=true enable_nav2:=false
    (create/refresh maps with: ros2 run r2d2_navigation save_map.sh during a
     bringup mapping session — writes maps/<name>_posegraph.{posegraph,data})

Architecture (differs from linorobot2 deliberately):
  * r2d2_motor runs with mapping_mode:=tank (nav2 Twist (vx, wz) -> MCU
    polar power/angle, the operator-confirmed differential kinematics)
  * NO wheel odom: rf2o_laser_odometry provides /odom + odom->base_link TF
    from the scan; slam_toolbox (mode: localization) localizes the serialized
    posegraph and owns map->odom. No AMCL, no map_server — slam_toolbox
    publishes /map itself.
  * Re-localization: RViz 2D Pose Estimate publishes /initialpose (handled
    natively by slam_toolbox).

nav2 core (planner/controller/bt/smoother/lifecycle) starts only when Nav2 is
installed; enable_nav2:=false brings up just motor + lidar + rf2o + localization.
"""
import os
import subprocess

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription
from launch.conditions import IfCondition
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
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
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("coin_d4_driver")
    _guard_against_stale_session(
        ["lidar_node", "r2d2_motor", "slam_toolbox", "rf2o_laser_odometry"])

    default_posegraph = os.path.join(nav_share, "maps", "r2d2_home_posegraph")

    args = [
        DeclareLaunchArgument("posegraph", default_value=default_posegraph,
                              description="serialized slam_toolbox posegraph (path WITHOUT the "
                                          ".posegraph/.data extensions), from save_map.sh"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="motor node mock UART"),
        DeclareLaunchArgument("enable_lidar", default_value="true"),
        DeclareLaunchArgument("lidar_port", default_value="/dev/ttyAMA1"),
        DeclareLaunchArgument("enable_slam_loc", default_value="true",
                              description="rf2o odom + slam_toolbox localization (map->odom TF); keep true on hardware"),
        DeclareLaunchArgument("enable_nav2", default_value="true",
                              description="nav2 servers (requires ros-jazzy-navigation2)"),
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
    # roll=pi: the COIN-D4 is mounted upside down (same convention as
    # r2d2_bringup's laser_tf_roll default) — the flip restores left/right.
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
            },
        ],
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )
    laser_tf = Node(
        package="tf2_ros",
        executable="static_transform_publisher",
        name="base_link_to_base_laser",
        arguments=["--x", "0", "--y", "0", "--z", "0.18", "--roll", "3.141592653589793",
                   "--frame-id", "base_link", "--child-frame-id", "base_laser"],
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )

    # --- laser odometry (same node/params as r2d2_bringup) ------------------------
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
            "freq": 20.0,
        }],
        condition=IfCondition(LaunchConfiguration("enable_slam_loc")),
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
        condition=IfCondition(LaunchConfiguration("enable_slam_loc")),
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
        condition=IfCondition(LaunchConfiguration("enable_slam_loc")),
    )

    # --- nav2 core ---------------------------------------------------------------
    params = RewrittenYaml(
        source_file=LaunchConfiguration("params_file"),
        root_key="",
        param_rewrites={
            "use_sim_time": LaunchConfiguration("use_sim_time"),
        },
        convert_types=True,
    )

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

    return LaunchDescription(args + [
        motor, lidar, laser_tf, rf2o, slam_loc, slam_lifecycle,
        planner, controller, smoother, behavior_server, bt_navigator,
        velocity_smoother, lifecycle_manager,
    ])
