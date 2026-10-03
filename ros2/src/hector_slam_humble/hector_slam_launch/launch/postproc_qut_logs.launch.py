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
            'base_frame': 'base_link',
            'odom_frame': 'base_link',
            'pub_map_odom_transform': 'true',
            'scan_subscriber_queue_size': '25',
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
        ])
    )

    # Static transform publisher (base_link to laser)
    map_nav_broadcaster = Node(
        package='tf2_ros',
        executable='static_transform_publisher',
        name='map_nav_broadcaster',
        arguments=['0', '0', '0', '0', '0', '0', 'base_link', 'laser'],
        output='screen'
    )

    return LaunchDescription([
        use_sim_time_arg,
        rviz_node,
        mapping_launch,
        geotiff_launch,
        map_nav_broadcaster,
    ])


