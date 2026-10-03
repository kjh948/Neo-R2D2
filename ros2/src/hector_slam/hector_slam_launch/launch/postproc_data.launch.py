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
            'tf_map_scanmatch_transform_frame_name': 'scanmatcher_frame2',
            'base_frame': 'base_footprint',
            'odom_frame': 'base_footprint',
            'pub_map_odom_transform': 'false',
        }.items()
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
            'trajectory_source_frame_name': 'scanmatcher_frame2',
        }.items()
    )

    return LaunchDescription([
        use_sim_time_arg,
        rviz_node,
        mapping_launch,
        geotiff_launch,
    ])


