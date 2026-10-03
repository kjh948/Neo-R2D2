cmake_minimum_required(VERSION 3.8)
project(hector_geotiff_plugins)

if(CMAKE_COMPILER_IS_GNUCXX OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  add_compile_options(-Wall -Wextra -Wpedantic)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(ament_cmake REQUIRED)
find_package(rclcpp REQUIRED)
find_package(hector_geotiff REQUIRED)
find_package(hector_nav_msgs REQUIRED)
find_package(pluginlib REQUIRED)
find_package(Qt5 COMPONENTS Widgets REQUIRED)

include_directories(${hector_geotiff_INCLUDE_DIRS})

add_library(hector_geotiff_plugins SHARED src/trajectory_geotiff_plugin.cpp)

ament_target_dependencies(hector_geotiff_plugins
  rclcpp
  hector_geotiff
  hector_nav_msgs
  pluginlib
)

install(TARGETS hector_geotiff_plugins
  DESTINATION lib
)

install(FILES
  hector_geotiff_plugins.xml
  DESTINATION share/${PROJECT_NAME}
)

ament_export_dependencies(
  rclcpp
  hector_geotiff
  hector_nav_msgs
  pluginlib
)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  ament_lint_auto_find_test_dependencies()
endif()

ament_package()
