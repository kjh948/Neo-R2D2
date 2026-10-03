#!/usr/bin/env python3
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue

'''
Parameter Description:
---
- Set laser scan directon:
  1. Set counterclockwise, example: {'laser_scan_dir': True}
  2. Set clockwise,        example: {'laser_scan_dir': False}
- Angle crop setting, Mask data within the set angle range:
  1. Enable angle crop fuction:
    1.1. enable angle crop,  example: {'enable_angle_crop_func': True}
    1.2. disable angle crop, example: {'enable_angle_crop_func': False}
  2. Angle cropping interval setting:
  - The distance and intensity data within the set angle range will be set to 0.
  - angle >= 'angle_crop_min' and angle <= 'angle_crop_max' which is [angle_crop_min, angle_crop_max], unit is degress.
    example:
      {'angle_crop_min': 135.0}
      {'angle_crop_max': 225.0}
      which is [135.0, 225.0], angle unit is degress.
'''

def generate_launch_description():
  # LDROBOT LiDAR publisher node
  ldlidar_node = Node(
      package='ldlidar_ros2',
      executable='ldlidar_ros2_node',
      name='ldlidar_publisher_ld19',
      output='screen',
      parameters=[
        {'product_name': LaunchConfiguration('product_name')},
        {'laser_scan_topic_name': 'scan'},
        {'point_cloud_2d_topic_name': 'pointcloud2d'},
        {'frame_id': LaunchConfiguration('frame_id')},
        {'port_name': LaunchConfiguration('port_name')},
        {'serial_baudrate': ParameterValue(LaunchConfiguration('serial_baudrate'), value_type=int)},
        {'laser_scan_dir': True},
        {'enable_angle_crop_func': False},
        {'angle_crop_min': 135.0},  # unit is degress
        {'angle_crop_max': 225.0},  # unit is degress
        {'range_min': 0.02}, # unit is meter
        {'range_max': 12.0}   # unit is meter
      ]
  )

  # base_link to base_laser tf node
  base_link_to_laser_tf_node = Node(
    package='tf2_ros',
    executable='static_transform_publisher',
    name='base_link_to_base_laser_ld19',
    arguments=[LaunchConfiguration('laser_tf_x'), '0', LaunchConfiguration('laser_tf_z'),
               '0', '0', '0', LaunchConfiguration('base_frame'), LaunchConfiguration('frame_id')]
  )


  # Define LaunchDescription variable
  ld = LaunchDescription()

  # Neo-R2D2 local fork: port_name used to be hard-coded to /dev/ttyUSB0. On the
  # Pi5 the LD19/LD14P hangs off a PL011 UART (/dev/ttyAMA1); /dev/ttyAMA0 is
  # reserved for the robot MCU.
  ld.add_action(DeclareLaunchArgument('port_name', default_value='/dev/ttyAMA1',
                                      description='LiDAR serial device (/dev/ttyAMA1, /dev/ttyUSB0, ...)'))
  ld.add_action(DeclareLaunchArgument('product_name', default_value='LDLiDAR_LD19',
                                      description="Node model switch; use 'LDLiDAR_LD14P' for the LD14P"))
  ld.add_action(DeclareLaunchArgument('serial_baudrate', default_value='230400'))
  ld.add_action(DeclareLaunchArgument('frame_id', default_value='base_laser'))
  ld.add_action(DeclareLaunchArgument('base_frame', default_value='base_link'))
  ld.add_action(DeclareLaunchArgument('laser_tf_x', default_value='0'))
  ld.add_action(DeclareLaunchArgument('laser_tf_z', default_value='0.18',
                                      description='laser height above base_link [m]'))

  ld.add_action(ldlidar_node)
  ld.add_action(base_link_to_laser_tf_node)

  return ld
