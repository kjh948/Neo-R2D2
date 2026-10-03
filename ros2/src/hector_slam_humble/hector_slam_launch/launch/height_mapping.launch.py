from launch import LaunchDescription
from launch_ros.actions import Node
from launch_ros.substitutions import FindPackageShare
from launch.substitutions import PathJoinSubstitution
from ament_index_python.packages import get_package_share_directory


def generate_launch_description():
    
    # Hector height mapping node
    hector_height_mapping_node = Node(
        package='hector_mapping',
        executable='hector_mapping_node',
        name='hector_height_mapping',
        output='screen',
        remappings=[
            ('map', 'height_map'),
            ('dynamic_map', 'height_map'),
        ],
        parameters=[{
            'scan_topic': 'laser0/scan',
            'base_frame': 'base_stabilized',
            'odom_frame': 'nav',
            'output_timing': False,
            'advertise_map_service': True,
            'use_tf_scan_transformation': True,
            'use_tf_pose_start_estimate': True,
            'pub_map_odom_transform': False,
            'map_with_known_poses': True,
            'map_pub_period': 5.0,
            'update_factor_free': 0.45,
            'map_update_distance_thresh': 0.0,
            'map_update_angle_thresh': 0.0,
            'map_resolution': 0.05,
            'map_size': 1024,
            'map_start_x': 0.5,
            'map_start_y': 0.5,
            'laser_min_dist': 0.6,
            'laser_max_dist': 3.9,
            'laser_z_min_value': 1.7,
            'laser_z_max_value': 2.1,
        }]
    )

    # Hector height geotiff node
    hector_height_geotiff_node = Node(
        package='hector_geotiff',
        executable='geotiff_node',
        name='hector_height_geotiff_node',
        output='screen',
        remappings=[('map', 'height_map')],
        parameters=[{
            'map_file_path': PathJoinSubstitution([FindPackageShare('hector_geotiff'), 'maps']),
            'map_file_base_name': 'RRL_2012_HectorDarmstadt_2m',
            'geotiff_save_period': 55.0,
            'draw_background_checkerboard': True,
            'draw_free_space_grid': True,
        }]
    )

    return LaunchDescription([
        hector_height_mapping_node,
        hector_height_geotiff_node,
    ])

