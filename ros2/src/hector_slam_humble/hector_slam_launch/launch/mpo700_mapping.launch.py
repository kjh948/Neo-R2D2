from launch import LaunchDescription
from launch.actions import IncludeLaunchDescription
from launch.substitutions import LaunchConfiguration, PathJoinSubstitution
from launch_ros.substitutions import FindPackageShare
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch_ros.actions import Node


def generate_launch_description():
    # Include mapping default launch
    mapping_launch = IncludeLaunchDescription(
        PythonLaunchDescriptionSource([
            PathJoinSubstitution([
                FindPackageShare('hector_mapping'),
                'launch',
                'mapping_default.launch.py'
            ])
        ]),
        launch_arguments={
            'base_frame': 'base_link',
            'odom_frame': 'wheelodom',
            'pub_map_odom_transform': 'true',
            'scan_topic': '/sick_s300/scan_unfiltered',
        }.items()
    )

    # Hector trajectory server node
    hector_trajectory_server_node = Node(
        package='hector_trajectory_server',
        executable='hector_trajectory_server',
        name='hector_trajectory_server',
        output='screen',
        parameters=[{
            'target_frame_name': '/map',
            'source_frame_name': '/base_link',
            'trajectory_update_rate': 4.0,
            'trajectory_publish_rate': 0.25,
        }]
    )

    # Hector geotiff node
    hector_geotiff_node = Node(
        package='hector_geotiff',
        executable='geotiff_node',
        name='hector_geotiff_node',
        output='screen',
        remappings=[('map', '/dynamic_map')],
        parameters=[{
            'map_file_path': PathJoinSubstitution([FindPackageShare('hector_geotiff'), 'maps']),
            'map_file_base_name': 'hector_slam_map',
            'geotiff_save_period': 60.0,
            'draw_background_checkerboard': True,
            'draw_free_space_grid': True,
        }]
    )

    # Map throttle node
    map_throttle_node = Node(
        package='topic_tools',
        executable='throttle',
        name='map_throttle',
        arguments=['messages', 'map', '0.015', 'map_thottled'],
        output='screen'
    )

    return LaunchDescription([
        mapping_launch,
        hector_trajectory_server_node,
        hector_geotiff_node,
        map_throttle_node,
    ])


