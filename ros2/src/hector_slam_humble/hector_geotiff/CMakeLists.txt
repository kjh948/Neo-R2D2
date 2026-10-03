cmake_minimum_required(VERSION 3.8)
project(hector_geotiff)

if(CMAKE_COMPILER_IS_GNUCXX OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  add_compile_options(-Wall -Wextra -Wpedantic)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(ament_cmake REQUIRED)
find_package(hector_map_tools REQUIRED)
find_package(hector_nav_msgs REQUIRED)
find_package(nav_msgs REQUIRED)
find_package(pluginlib REQUIRED)
find_package(rclcpp REQUIRED)
find_package(std_msgs REQUIRED)
find_package(Qt5 COMPONENTS Widgets REQUIRED)
find_package(eigen3_cmake_module REQUIRED)
find_package(Eigen3 REQUIRED)

include_directories(include)

add_library(geotiff_writer src/geotiff_writer/geotiff_writer.cpp)
ament_target_dependencies(geotiff_writer
  hector_map_tools
  hector_nav_msgs
  nav_msgs
  pluginlib
  rclcpp
  std_msgs
  Eigen3
)
target_link_libraries(geotiff_writer Qt5::Widgets)

add_executable(geotiff_saver src/geotiff_saver.cpp)
target_link_libraries(geotiff_saver geotiff_writer)
ament_target_dependencies(geotiff_saver
  hector_map_tools
  hector_nav_msgs
  nav_msgs
  pluginlib
  rclcpp
  std_msgs
)

add_executable(geotiff_node src/geotiff_node.cpp)
target_link_libraries(geotiff_node geotiff_writer)
ament_target_dependencies(geotiff_node
  hector_map_tools
  hector_nav_msgs
  nav_msgs
  pluginlib
  rclcpp
  std_msgs
)

install(TARGETS geotiff_writer
  EXPORT export_${PROJECT_NAME}
  ARCHIVE DESTINATION lib
  LIBRARY DESTINATION lib
  RUNTIME DESTINATION bin
)

# Install the export set
install(EXPORT export_${PROJECT_NAME}
  FILE export_${PROJECT_NAME}Export.cmake
  NAMESPACE hector_geotiff::
  DESTINATION share/${PROJECT_NAME}/cmake
)

install(TARGETS geotiff_saver geotiff_node
  DESTINATION lib/${PROJECT_NAME}
)

install(DIRECTORY include/
  DESTINATION include
  FILES_MATCHING PATTERN "*.h"
)

ament_export_include_directories(include)
ament_export_targets(export_${PROJECT_NAME} HAS_LIBRARY_TARGET)
ament_export_dependencies(
  hector_map_tools
  hector_nav_msgs
  nav_msgs
  pluginlib
  rclcpp
  std_msgs
  Eigen3
)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  ament_lint_auto_find_test_dependencies()
endif()

ament_package()
