from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument, GroupAction
from launch.conditions import UnlessCondition
from launch.substitutions import LaunchConfiguration, EnvironmentVariable
from launch_ros.actions import Node
from launch_ros.substitutions import FindPackageShare


def generate_launch_description():
    # Declare launch arguments
    real_robot_arg = DeclareLaunchArgument(
        'real_robot',
        default_value=EnvironmentVariable('REALROBOT', default_value='false'),
        description='Whether this is a real robot'
    )

    # Hector mapping node
    hector_mapping_node = Node(
        package='hector_mapping',
        executable='hector_mapping_node',
        name='hector_mapping',
        output='screen',
        remappings=[('map', 'scanmatcher_map')],
        parameters=[{
            'scan_topic': 'laser1/scan',
            'base_frame': 'base_stabilized',
            'output_timing': False,
            'use_tf_scan_transformation': True,
            'use_tf_pose_start_estimate': LaunchConfiguration('use_tf_pose_start_estimate'),
            'map_pub_period': 1.0,
            'laser_z_min_value': -0.3,
            'update_factor_free': 0.3,
            'map_resolution': 0.05,
            'map_size': 1024,
            'map_start_x': 0.5,
            'map_start_y': 0.5,
            'map_multi_res_levels': 1,
            'odom_frame': 'nav',
            'pub_map_odom_transform': False,
        }]
    )

    # Conditional parameter setting
    use_tf_pose_start_estimate_arg = DeclareLaunchArgument(
        'use_tf_pose_start_estimate',
        default_value='false',
        description='Whether to use TF pose start estimate'
    )

    # Static transform publisher (map to nav)
    map_nav_broadcaster = Node(
        package='tf2_ros',
        executable='static_transform_publisher',
        name='map_nav_broadcaster',
        arguments=['0', '0', '0', '0', '0', '0', 'map', 'nav'],
        output='screen'
    )

    return LaunchDescription([
        real_robot_arg,
        use_tf_pose_start_estimate_arg,
        hector_mapping_node,
        map_nav_broadcaster,
    ])


