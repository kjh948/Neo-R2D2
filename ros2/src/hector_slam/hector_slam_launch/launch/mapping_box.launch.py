from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, GroupAction
from launch.conditions import IfCondition, UnlessCondition
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from ament_index_python.packages import get_package_share_directory
import os


def generate_launch_description():
    # Declare launch arguments
    disable_poseupdate_arg = DeclareLaunchArgument(
        'disable_poseupdate',
        default_value='false',
        description='Whether to disable pose update'
    )

    # Hector mapping node
    hector_mapping_node = Node(
        package='hector_mapping',
        executable='hector_mapping_node',
        name='hector_mapping',
        output='screen',
        parameters=[{
            'base_frame': 'base_stabilized',
            'odom_frame': 'nav',
            'output_timing': False,
            'use_tf_scan_transformation': True,
            'use_tf_pose_start_estimate': False,
            'scan_topic': 'scan',
            'map_resolution': 0.050,
            'map_size': 2048,
            'map_start_x': 0.5,
            'map_start_y': 0.5,
            'update_factor_free': 0.4,
            'update_factor_occupied': 0.9,
            'map_update_distance_thresh': 0.4,
            'map_update_angle_thresh': 0.06,
            'pub_map_odom_transform': LaunchConfiguration('pub_map_odom_transform'),
        }],
        remappings=[
            ('poseupdate', LaunchConfiguration('poseupdate_topic')),
        ]
    )

    # Conditional groups
    disable_poseupdate_group = GroupAction(
        condition=IfCondition(LaunchConfiguration('disable_poseupdate')),
        actions=[
            Node(
                package='hector_mapping',
                executable='hector_mapping_node',
                name='hector_mapping',
                output='screen',
                parameters=[{
                    'pub_map_odom_transform': True,
                }],
                remappings=[('poseupdate', 'poseupdate_disabled')]
            )
        ]
    )

    enable_poseupdate_group = GroupAction(
        condition=UnlessCondition(LaunchConfiguration('disable_poseupdate')),
        actions=[
            Node(
                package='tf2_ros',
                executable='static_transform_publisher',
                name='map_nav_broadcaster',
                arguments=['0', '0', '0', '0', '0', '0', 'map', 'nav'],
                output='screen'
            )
        ]
    )

    # Map throttle node (using topic_tools in ROS2)
    map_throttle_node = Node(
        package='topic_tools',
        executable='throttle',
        name='map_throttle',
        arguments=['messages', 'map', '0.1', 'throttled_map'],
        output='screen'
    )

    return LaunchDescription([
        disable_poseupdate_arg,
        DeclareLaunchArgument('pub_map_odom_transform', default_value='false'),
        DeclareLaunchArgument('poseupdate_topic', default_value='poseupdate'),
        hector_mapping_node,
        disable_poseupdate_group,
        enable_poseupdate_group,
        map_throttle_node,
    ])


