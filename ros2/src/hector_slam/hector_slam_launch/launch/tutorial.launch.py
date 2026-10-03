from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, IncludeLaunchDescription
from launch.substitutions import LaunchConfiguration, PathJoinSubstitution
from launch_ros.substitutions import FindPackageShare
from launch.launch_description_sources import PythonLaunchDescriptionSource


def generate_launch_description():
    # Declare launch arguments
    geotiff_map_file_path_arg = DeclareLaunchArgument(
        'geotiff_map_file_path',
        default_value=PathJoinSubstitution([FindPackageShare('hector_geotiff'), 'maps']),
        description='Path to geotiff map files'
    )
    
    use_sim_time_arg = DeclareLaunchArgument(
        'use_sim_time',
        default_value='true',
        description='Whether to use simulation time'
    )

    # Include mapping default launch
    mapping_launch = IncludeLaunchDescription(
        PythonLaunchDescriptionSource([
            PathJoinSubstitution([
                FindPackageShare('hector_mapping'),
                'launch',
                'mapping_default.launch.py'
            ])
        ])
    )

    # Include geotiff mapper launch
    geotiff_launch = IncludeLaunchDescription(
        PythonLaunchDescriptionSource([
            PathJoinSubstitution([
                FindPackageShare('hector_geotiff_launch'),
                'launch',
                'geotiff_mapper.launch.py'
            ])
        ]),
        launch_arguments={
            'trajectory_source_frame_name': 'scanmatcher_frame',
            'map_file_path': LaunchConfiguration('geotiff_map_file_path'),
        }.items()
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

    return LaunchDescription([
        geotiff_map_file_path_arg,
        use_sim_time_arg,
        mapping_launch,
        geotiff_launch,
        rviz_node,
    ])


