import glob
import os
from setuptools import setup

package_name = "r2d2_vision"

setup(
    name=package_name,
    version="0.1.0",
    packages=[package_name,
              f"{package_name}.bytetrack_lite",
              f"{package_name}.bytetrack_lite.utils"],
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
        (os.path.join("share", package_name, "launch"), glob.glob("launch/*.launch.py")),
        (os.path.join("share", package_name, "config"), glob.glob("config/*.yaml")),
        (os.path.join("share", package_name, "tools"), glob.glob("r2d2_vision/tools/*.py")),
        (os.path.join("share", package_name, "scripts"), glob.glob("scripts/*.py")),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="kjh948",
    maintainer_email="kjh948@example.com",
    description="NCNN YOLO detection/tracking over ROS 2 (yolo_ros-flavored interface), "
                "torch-free runtime; vision_export handles offline YOLOE class-set builds.",
    license="MIT",
    entry_points={
        "console_scripts": [
            "detection_node = r2d2_vision.detection_node:main",
            "vision_export = r2d2_vision.vision_export:main",
        ],
    },
)
