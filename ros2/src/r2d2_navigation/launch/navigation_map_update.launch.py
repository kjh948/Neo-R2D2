#!/usr/bin/env python3
"""Neo-R2D2 navigation WITH live slam_toolbox map updates (upstream behavior).

Identical to navigation.launch.py except map_updates:=true: every
map_update_interval the localization node re-rasterizes loaded graph + recent
live scans and republishes /map, and loop closure may adjust old nodes — i.e.
driving visibly mutates the map (new obstacles appear, stale ones decay).
Use when the environment changed and the driven map should absorb it, or
right before refreshing the posegraph with save_map.sh. Daily runs should use
navigation.launch.py (frozen map).

    ros2 launch r2d2_navigation navigation_map_update.launch.py
    ros2 launch r2d2_navigation navigation_map_update.launch.py posegraph:=<abs>/maps/r2d2_home_posegraph

All launch arguments are passed straight through to navigation.launch.py
(its declared args and defaults are reused here, so the two never drift).
"""
import importlib.util
import os

from ament_index_python.packages import get_package_share_directory
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import LaunchConfiguration


def generate_launch_description():
    nav_share = get_package_share_directory("r2d2_navigation")
    main_launch = os.path.join(nav_share, "launch", "navigation.launch.py")

    # Reuse the main launch's argument declarations verbatim so this wrapper
    # cannot drift out of sync with it.
    spec = importlib.util.spec_from_file_location("_r2d2_navigation_launch", main_launch)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    arg_entities = [
        e for e in module.generate_launch_description().entities
        if isinstance(e, DeclareLaunchArgument) and e.name != "map_updates"
    ]

    forward = {a.name: LaunchConfiguration(a.name) for a in arg_entities}
    forward["map_updates"] = "true"
    return LaunchDescription(arg_entities + [
        IncludeLaunchDescription(
            PythonLaunchDescriptionSource(main_launch),
            launch_arguments=forward.items()),
    ])
