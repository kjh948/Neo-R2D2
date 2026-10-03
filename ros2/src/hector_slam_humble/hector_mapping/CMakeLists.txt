cmake_minimum_required(VERSION 3.8)
project(hector_mapping)

if(CMAKE_COMPILER_IS_GNUCXX OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  add_compile_options(-Wall -Wextra -Wpedantic)
endif()

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# find dependencies
find_package(ament_cmake REQUIRED)
find_package(rclcpp REQUIRED)
find_package(nav_msgs REQUIRED)
find_package(visualization_msgs REQUIRED)
find_package(tf2 REQUIRED)
find_package(tf2_ros REQUIRED)
find_package(tf2_geometry_msgs REQUIRED)
find_package(message_filters REQUIRED)
find_package(laser_geometry REQUIRED)
find_package(std_srvs REQUIRED)
find_package(eigen3_cmake_module REQUIRED)
find_package(Eigen3 REQUIRED)
find_package(hector_nav_msgs REQUIRED)
find_package(hector_marker_drawing REQUIRED)
find_package(hector_map_tools REQUIRED)
find_package(rosidl_default_generators REQUIRED)
find_package(Boost REQUIRED COMPONENTS thread)

# Generate messages and services
rosidl_generate_interfaces(${PROJECT_NAME}
  "msg/HectorDebugInfo.msg"
  "msg/HectorIterData.msg"
  "srv/ResetMapping.srv"
  DEPENDENCIES geometry_msgs
)

# Include directories
include_directories(include/hector_slam_lib)
include_directories(
  ${Boost_INCLUDE_DIRS}
  ${EIGEN3_INCLUDE_DIRS}
)

# Build executable
add_executable(hector_mapping_node
  src/HectorDebugInfoProvider.h
  src/HectorDrawings.h
  src/HectorMappingRos.h
  src/HectorMappingRos.cpp
  src/main.cpp
  src/PoseInfoContainer.cpp
  src/PoseInfoContainer.h
)

ament_target_dependencies(hector_mapping_node
  rclcpp
  nav_msgs
  visualization_msgs
  tf2
  tf2_ros
  tf2_geometry_msgs
  message_filters
  laser_geometry
  std_srvs
  Eigen3
  hector_nav_msgs
  hector_marker_drawing
  hector_map_tools
)

# Link against generated message interfaces
rosidl_get_typesupport_target(cpp_typesupport_target
  ${PROJECT_NAME} "rosidl_typesupport_cpp")
target_link_libraries(hector_mapping_node "${cpp_typesupport_target}")

target_link_libraries(hector_mapping_node
  ${Boost_LIBRARIES}
)

# Install
install(TARGETS hector_mapping_node
  DESTINATION lib/${PROJECT_NAME}
)

install(DIRECTORY include/hector_slam_lib/
  DESTINATION include
  FILES_MATCHING PATTERN "*.h"
  PATTERN ".svn" EXCLUDE
)

install(DIRECTORY launch/
  DESTINATION share/${PROJECT_NAME}/launch
)

ament_export_include_directories(include)
ament_export_dependencies(
  rclcpp
  nav_msgs
  visualization_msgs
  tf2
  tf2_ros
  tf2_geometry_msgs
  message_filters
  laser_geometry
  std_srvs
  Eigen3
  hector_nav_msgs
  hector_marker_drawing
  hector_map_tools
)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  ament_lint_auto_find_test_dependencies()
endif()

ament_package()
