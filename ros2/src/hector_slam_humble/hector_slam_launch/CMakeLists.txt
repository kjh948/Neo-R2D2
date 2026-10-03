cmake_minimum_required(VERSION 3.8)
project(hector_slam_launch)

find_package(ament_cmake REQUIRED)

install(DIRECTORY launch rviz_cfg
  DESTINATION share/${PROJECT_NAME}
)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  ament_lint_auto_find_test_dependencies()
endif()

ament_package()
