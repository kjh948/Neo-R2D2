from setuptools import find_packages, setup

package_name = "r2d2_motor"

setup(
    name=package_name,
    version="0.1.0",
    packages=find_packages(exclude=["test"]),
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
        (f"share/{package_name}/launch", ["launch/motor.launch.py"]),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="kjh948",
    maintainer_email="kjh948@example.com",
    description="ROS 2 node that drives the R2-D2 MCU from geometry_msgs/Twist commands.",
    license="MIT",
    tests_require=["pytest"],
    entry_points={
        "console_scripts": [
            "motor_node = r2d2_motor.motor_node:main",
        ],
    },
)
