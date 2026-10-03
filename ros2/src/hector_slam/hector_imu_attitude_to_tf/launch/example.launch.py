from launch import LaunchDescription
from launch_ros.actions import Node


def generate_launch_description():
    # IMU attitude to TF node
    imu_attitude_to_tf_node = Node(
        package='hector_imu_attitude_to_tf',
        executable='imu_attitude_to_tf_node',
        name='imu_attitude_to_tf_node',
        output='screen',
        remappings=[('imu_topic', 'thumper_imu')],
        parameters=[{
            'base_stabilized_frame': 'base_stabilized',
            'base_frame': 'base_footprint',
        }]
    )

    return LaunchDescription([
        imu_attitude_to_tf_node,
    ])


