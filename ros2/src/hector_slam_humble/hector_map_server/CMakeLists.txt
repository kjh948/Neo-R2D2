cmake_minimum_required(VERSION 3.8)
project(hector_map_server)

if(CMAKE_COMPILER_IS_GNUCXX OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  add_compile_options(-Wall -Wextra -Wpedantic)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(ament_cmake REQUIRED)
find_package(rclcpp REQUIRED)
find_package(hector_map_tools REQUIRED)
find_package(hector_marker_drawing REQUIRED)
find_package(hector_nav_msgs REQUIRED)
find_package(nav_msgs REQUIRED)
find_package(tf2 REQUIRED)
find_package(tf2_ros REQUIRED)
find_package(tf2_geometry_msgs REQUIRED)

add_executable(hector_map_server src/hector_map_server.cpp)

ament_target_dependencies(hector_map_server
  rclcpp
  hector_map_tools
  hector_marker_drawing
  hector_nav_msgs
  nav_msgs
  tf2
  tf2_ros
  tf2_geometry_msgs
)

install(TARGETS hector_map_server
  DESTINATION lib/${PROJECT_NAME}
)

ament_export_dependencies(
  rclcpp
  hector_map_tools
  hector_marker_drawing
  hector_nav_msgs
  nav_msgs
  tf2
  tf2_ros
  tf2_geometry_msgs
)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  ament_lint_auto_find_test_dependencies()
endif()

ament_package()
