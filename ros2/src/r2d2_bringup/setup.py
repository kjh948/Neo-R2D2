import glob
import os
from setuptools import setup

package_name = "r2d2_bringup"

setup(
    name=package_name,
    version="0.1.0",
    packages=[package_name],
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
        (os.path.join("share", package_name, "launch"), glob.glob("launch/*.launch.py")),
        (os.path.join("share", package_name, "config"), glob.glob("config/*.yaml")),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="kjh948",
    maintainer_email="kjh948@example.com",
    description="Bringup launch for Neo-R2D2: motor node, LDROBOT lidar, hector SLAM.",
    license="MIT",
    entry_points={},
)
