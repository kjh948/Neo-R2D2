"""Launch the R2-D2 sound-effects node.

    ros2 launch r2d2_sound sound.launch.py
    ros2 launch r2d2_sound sound.launch.py mock:=true volume:=60
    ros2 launch r2d2_sound sound.launch.py sound_dir:=/path/to/sound_effects
"""
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue


def generate_launch_description() -> LaunchDescription:
    return LaunchDescription([
        DeclareLaunchArgument(
            "sound_dir", default_value="/home/kjh948/workspace/Neo-R2D2/sound_effects",
            description="Directory holding the named .mp3/.wav clips (r2d2/sound.py table)"),
        DeclareLaunchArgument("volume", default_value="100",
                              description="0-100; applied per-player (paplay/ffplay scale)"),
        DeclareLaunchArgument("mute", default_value="false"),
        DeclareLaunchArgument("mock", default_value="false",
                              description="Log plays instead of touching the audio device"),
        Node(
            package="r2d2_sound",
            executable="sound_node",
            name="r2d2_sound",
            output="screen",
            parameters=[{
                "sound_dir": ParameterValue(LaunchConfiguration("sound_dir"), value_type=str),
                "volume": ParameterValue(LaunchConfiguration("volume"), value_type=int),
                "mute": ParameterValue(LaunchConfiguration("mute"), value_type=bool),
                "mock": ParameterValue(LaunchConfiguration("mock"), value_type=bool),
            }],
        ),
    ])
