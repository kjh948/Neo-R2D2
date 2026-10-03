from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from ament_index_python.packages import get_package_share_directory
import os


def generate_launch_description():
    # Get package share directory
    hector_geotiff_dir = get_package_share_directory('hector_geotiff')
    
    # Declare launch arguments
    trajectory_source_frame_name_arg = DeclareLaunchArgument(
        'trajectory_source_frame_name',
        default_value='/base_link',
        description='Source frame name for trajectory'
    )
    
    trajectory_update_rate_arg = DeclareLaunchArgument(
        'trajectory_update_rate',
        default_value='4',
        description='Trajectory update rate'
    )
    
    trajectory_publish_rate_arg = DeclareLaunchArgument(
        'trajectory_publish_rate',
        default_value='0.25',
        description='Trajectory publish rate'
    )
    
    map_file_path_arg = DeclareLaunchArgument(
        'map_file_path',
        default_value=os.path.join(hector_geotiff_dir, 'maps'),
        description='Path to map files'
    )
    
    map_file_base_name_arg = DeclareLaunchArgument(
        'map_file_base_name',
        default_value='hector_slam_map',
        description='Base name for map files'
    )

    # Hector trajectory server node
    hector_trajectory_server_node = Node(
        package='hector_trajectory_server',
        executable='hector_trajectory_server',
        name='hector_trajectory_server',
        output='screen',
        parameters=[{
            'target_frame_name': '/map',
            'source_frame_name': LaunchConfiguration('trajectory_source_frame_name'),
            'trajectory_update_rate': LaunchConfiguration('trajectory_update_rate'),
            'trajectory_publish_rate': LaunchConfiguration('trajectory_publish_rate'),
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
            'map_file_path': LaunchConfiguration('map_file_path'),
            'map_file_base_name': LaunchConfiguration('map_file_base_name'),
            'geotiff_save_period': 0.0,
            'draw_background_checkerboard': True,
            'draw_free_space_grid': True,
            'plugins': 'hector_geotiff_plugins/TrajectoryMapWriter',
        }]
    )

    return LaunchDescription([
        trajectory_source_frame_name_arg,
        trajectory_update_rate_arg,
        trajectory_publish_rate_arg,
        map_file_path_arg,
        map_file_base_name_arg,
        hector_trajectory_server_node,
        hector_geotiff_node,
    ])


