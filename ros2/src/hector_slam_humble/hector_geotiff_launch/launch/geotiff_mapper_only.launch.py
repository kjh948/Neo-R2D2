from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration, PathJoinSubstitution
from launch_ros.actions import Node
from launch_ros.substitutions import FindPackageShare


def generate_launch_description():
    # Declare launch arguments
    map_file_path_arg = DeclareLaunchArgument(
        'map_file_path',
        default_value=PathJoinSubstitution([FindPackageShare('hector_geotiff'), 'maps']),
        description='Path to map files'
    )
    
    map_file_base_name_arg = DeclareLaunchArgument(
        'map_file_base_name',
        default_value='',
        description='Base name for map files'
    )
    
    geotiff_save_period_arg = DeclareLaunchArgument(
        'geotiff_save_period',
        default_value='45',
        description='Geotiff save period in seconds'
    )

    # Hector geotiff node
    hector_geotiff_node = Node(
        package='hector_geotiff',
        executable='geotiff_node',
        name='hector_geotiff_node',
        output='screen',
        remappings=[('map', 'scanmatcher_map')],
        parameters=[{
            'map_file_path': LaunchConfiguration('map_file_path'),
            'map_file_base_name': LaunchConfiguration('map_file_base_name'),
            'geotiff_save_period': LaunchConfiguration('geotiff_save_period'),
            'plugins': 'hector_geotiff_plugins/TrajectoryMapWriter hector_worldmodel_geotiff_plugins/QRCodeMapWriter hector_worldmodel_geotiff_plugins/VictimMapWriter',
            'VictimMapWriter/draw_all_objects': False,
            'VictimMapWriter/class_id': 'victim',
            'QRCodeMapWriter/draw_all_objects': True,
            'QRCodeMapWriter/class_id': 'qrcode',
        }]
    )

    return LaunchDescription([
        map_file_path_arg,
        map_file_base_name_arg,
        geotiff_save_period_arg,
        hector_geotiff_node,
    ])


