#!/usr/bin/env python3
"""Neo-R2D2 navigation: sensors + hector localization + Nav2 on a saved map.

    ros2 launch r2d2_navigation navigation.launch.py map:=maps/room.yaml
    ros2 launch r2d2_navigation navigation.launch.py mock:=true enable_nav2:=false

Architecture (differs from linorobot2 deliberately):
  * r2d2_motor runs with mapping_mode:=tank (nav2 Twist (vx, wz) -> MCU
    polar power/angle, the operator-confirmed differential kinematics)
  * hector_mapping stays active: it owns the map->base_link TF (scan matching,
    no odom, no AMCL). Its live map is remapped to /live_map so map_server's
    saved /map is the only costmap input.
  * Re-localization: RViz 2D Pose Estimate publishes /initialpose (hector resetPose).

nav2 core (planner/controller/bt/smoother/lifecycle) starts only when Nav2 is
installed; enable_nav2:=false brings up just motor + lidar + hector + map_server.
"""
import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription
from launch.conditions import IfCondition
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from nav2_common.launch import RewrittenYaml


def generate_launch_description():
    nav_share = get_package_share_directory("r2d2_navigation")
    motor_share = get_package_share_directory("r2d2_motor")
    lidar_share = get_package_share_directory("ldlidar_ros2")

    default_map = os.path.join(nav_share, "maps", "placeholder.yaml")

    args = [
        DeclareLaunchArgument("map", default_value=default_map,
                              description="saved map .yaml (map_saver_cli of a hector session)"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="motor node mock UART"),
        DeclareLaunchArgument("enable_lidar", default_value="true"),
        DeclareLaunchArgument("lidar_port", default_value="/dev/ttyAMA1"),
        DeclareLaunchArgument("enable_hector", default_value="true",
                              description="hector provides map->base_link TF; keep true on hardware"),
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

    lidar = IncludeLaunchDescription(
        PythonLaunchDescriptionSource(os.path.join(lidar_share, "launch", "ld19.launch.py")),
        launch_arguments={"port_name": LaunchConfiguration("lidar_port")}.items(),
        condition=IfCondition(LaunchConfiguration("enable_lidar")),
    )

    hector = Node(
        package="hector_mapping",
        executable="hector_mapping_node",
        name="hector_slam_nav",
        output="screen",
        parameters=[
            RewrittenYaml(
                source_file=os.path.join(nav_share, "config", "hector_nav.yaml"),
                root_key="",
                param_rewrites={"use_sim_time": LaunchConfiguration("use_sim_time")},
                convert_types=True,
            )
        ],
        remappings=[
            ("map", "live_map"),
            ("map_metadata", "live_map_metadata"),
            ("rosout", "/r2d2_hector/rosout"),
        ],
        condition=IfCondition(LaunchConfiguration("enable_hector")),
    )

    map_server = Node(
        package="nav2_map_server",
        executable="map_server",
        name="map_server",
        output="screen",
        parameters=[{
            "use_sim_time": LaunchConfiguration("use_sim_time"),
            "yaml_filename": LaunchConfiguration("map"),
        }],
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

    lifecycle_nodes = ["map_server", "planner_server", "controller_server",
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
        motor, lidar, hector, map_server,
        planner, controller, smoother, behavior_server, bt_navigator,
        velocity_smoother, lifecycle_manager,
    ])
