#!/usr/bin/env python3
"""Neo-R2D2 remote-PC visualization: RViz 2 against the robot's bringup stack.

Run this on the remote PC (not the Pi). The robot side runs:
    ros2 launch r2d2_bringup r2d2_bringup.launch.py      # SLAM session

    ros2 launch r2d2_bringup remote.launch.py                        # SLAM view (/map+/scan+TF)
    ros2 launch r2d2_bringup remote.launch.py rviz_config:=<path>/nav2_config.rviz
                                                                     # nav session view
                                                                     # (r2d2_navigation/share/r2d2_navigation/rviz/)

Requirements on the remote PC: ROS 2 Jazzy desktop (rviz2) and the same
ROS_DOMAIN_ID as the robot — see ros2/README.md §7. This launch itself only
starts rviz2, so the remote does NOT need the Pi-only drivers (coin_d4_driver,
hector_slam, ...) built; installing just this workspace's r2d2_bringup package
(ament_python, no deps at build time) is enough to get the launch + configs.
"""
import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, LogInfo
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node


def generate_launch_description():
    bringup_share = get_package_share_directory("r2d2_bringup")

    args = [
        DeclareLaunchArgument("rviz_config",
                              default_value=os.path.join(bringup_share, "config", "rviz_slam.rviz"),
                              description=".rviz file; use r2d2_navigation's nav2_config.rviz "
                                          "for the navigation session"),
    ]

    rviz = Node(
        package="rviz2",
        executable="rviz2",
        name="r2d2_remote_rviz",
        output="screen",
        arguments=["-d", LaunchConfiguration("rviz_config")],
    )

    # Surface common DDS pitfalls up front instead of "rviz is just empty".
    # os.environ is read on the machine running this launch (the remote PC).
    domain_id = os.environ.get("ROS_DOMAIN_ID", "0")
    rmw = os.environ.get("RMW_IMPLEMENTATION", "<unset -> FastDDS default>")
    hints = LogInfo(
        msg=f"[remote] ROS_DOMAIN_ID={domain_id}, RMW_IMPLEMENTATION={rmw} — "
            "both must match the Pi (Pi: RMW=rmw_cyclonedds_cpp, see ~/.bashrc). "
            "If /map or /scan do not appear: same LAN subnet, `ros2 topic list` on both "
            "sides, AP isolation is the usual cause. See ros2/README.md §7.")

    return LaunchDescription(args + [hints, rviz])
