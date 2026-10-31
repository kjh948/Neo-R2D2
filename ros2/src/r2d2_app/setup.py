from glob import glob
import os

from setuptools import find_packages, setup

package_name = "r2d2_app"

setup(
    name=package_name,
    version="0.1.0",
    packages=find_packages(exclude=["test"]),
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
        (os.path.join("share", package_name, "launch"), glob("launch/*.launch.py")),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="kjh948",
    maintainer_email="kjh948@example.com",
    description="Application-level ROS 2 behaviors for Neo-R2D2.",
    license="MIT",
    tests_require=["pytest"],
    entry_points={
        "console_scripts": [
            "person_tracker_node = r2d2_app.person_tracker_node:main",
        ],
    },
)
