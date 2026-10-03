from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription
from launch.substitutions import LaunchConfiguration, PathJoinSubstitution
from launch_ros.substitutions import FindPackageShare
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch_ros.actions import Node


def generate_launch_description():
    # Declare launch arguments
    use_sim_time_arg = DeclareLaunchArgument(
        'use_sim_time',
        default_value='true',
        description='Whether to use simulation time'
    )

    # RViz node
    rviz_node = Node(
        package='rviz2',
        executable='rviz2',
        name='rviz',
        arguments=['-d', PathJoinSubstitution([
            FindPackageShare('hector_slam_launch'),
            'rviz_cfg',
            'mapping_demo.rviz'
        ])],
        parameters=[{
            'use_sim_time': LaunchConfiguration('use_sim_time'),
        }]
    )

    # Hector mapping node
    hector_mapping_node = Node(
        package='hector_mapping',
        executable='hector_mapping_node',
        name='hector_mapping',
        output='screen',
        parameters=[{
            'scan_topic': 'scan',
            'base_frame': 'laser',
            'odom_frame': 'laser',
            'output_timing': False,
            'use_tf_scan_transformation': True,
            'use_tf_pose_start_estimate': False,
            'pub_map_odom_transform': True,
            'advertise_map_service': True,
            'map_resolution': 0.050,
            'map_size': 2048,
            'map_start_x': 0.75,
            'map_start_y': 0.25,
            'map_multi_res_levels': 3,
            'update_factor_free': 0.4,
            'update_factor_occupied': 0.95,
            'map_update_distance_thresh': 0.3,
            'map_update_angle_thresh': 0.03,
        }]
    )

    # Hector trajectory server node
    hector_trajectory_server_node = Node(
        package='hector_trajectory_server',
        executable='hector_trajectory_server',
        name='hector_trajectory_server',
        output='screen',
        parameters=[{
            'target_frame_name': '/map',
            'source_frame_name': 'laser',
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
            'geotiff_save_period': 10.0,
            'draw_background_checkerboard': True,
            'draw_free_space_grid': True,
        }]
    )

    return LaunchDescription([
        use_sim_time_arg,
        rviz_node,
        hector_mapping_node,
        hector_trajectory_server_node,
        hector_geotiff_node,
    ])


