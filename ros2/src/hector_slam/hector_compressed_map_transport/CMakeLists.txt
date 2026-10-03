cmake_minimum_required(VERSION 3.8)
project(hector_compressed_map_transport)

if(CMAKE_COMPILER_IS_GNUCXX OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  add_compile_options(-Wall -Wextra -Wpedantic)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(ament_cmake REQUIRED)
find_package(cv_bridge REQUIRED)
find_package(geometry_msgs REQUIRED)
find_package(hector_map_tools REQUIRED)
find_package(image_transport REQUIRED)
find_package(nav_msgs REQUIRED)
find_package(sensor_msgs REQUIRED)
find_package(eigen3_cmake_module REQUIRED)
find_package(Eigen3 REQUIRED)

add_executable(map_to_image_node src/map_to_image_node.cpp)

ament_target_dependencies(map_to_image_node
  rclcpp
  cv_bridge
  geometry_msgs
  hector_map_tools
  image_transport
  nav_msgs
  sensor_msgs
  Eigen3
)

install(TARGETS map_to_image_node
  DESTINATION lib/${PROJECT_NAME}
)

ament_export_dependencies(
  cv_bridge
  geometry_msgs
  hector_map_tools
  image_transport
  nav_msgs
  sensor_msgs
  Eigen3
)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  ament_lint_auto_find_test_dependencies()
endif()

ament_package()
