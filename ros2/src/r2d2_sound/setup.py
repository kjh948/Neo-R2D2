from setuptools import find_packages, setup

package_name = "r2d2_sound"

setup(
    name=package_name,
    version="0.1.0",
    packages=find_packages(exclude=["test"]),
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
        (f"share/{package_name}/launch", ["launch/sound.launch.py"]),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="kjh948",
    maintainer_email="kjh948@example.com",
    description="ROS 2 sound-effects node for the R2-D2 (vendored r2d2 sound engine).",
    license="MIT",
    tests_require=["pytest"],
    entry_points={
        "console_scripts": [
            "sound_node = r2d2_sound.sound_node:main",
        ],
    },
)
