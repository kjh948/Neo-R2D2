from launch import LaunchDescription
from launch_ros.actions import Node


def generate_launch_description():
    # Pose and orientation to IMU node
    pose_and_orientation_to_imu_node = Node(
        package='hector_imu_tools',
        executable='pose_and_orientation_to_imu_node',
        name='pose_and_orientation_to_imu_node',
        output='screen',
        remappings=[
            ('/imu', '/imu_quat'),
            ('/fused_imu', '/imu_in'),
            ('/pose', '/slam_out_pose'),
            # ('/state', '/state_imu'),  # Commented out as in original
        ]
    )

    return LaunchDescription([
        pose_and_orientation_to_imu_node,
    ])


