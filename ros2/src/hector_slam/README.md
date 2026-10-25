# Hector SLAM for ROS 2 Humble

This is a complete migration of the Hector SLAM package from ROS 1 to ROS 2 Humble.
Hector SLAM is a flexible and extensible SLAM system for robots equipped with
LIDAR sensors.

## Migration Summary

This package has been fully migrated from ROS 1 (catkin) to ROS 2 Humble (ament_cmake).
All components have been updated to use ROS 2 APIs:

### 1. Build System Migration
- Changed from catkin to ament_cmake
- Updated all CMakeLists.txt files
- Updated all package.xml files to format 2/3

### 2. Code Migration
- Replaced `ros::NodeHandle` with `rclcpp::Node`
- Updated all message types (`::msg::` namespace)
- Updated all service types (`::srv::` namespace)
- Migrated from tf to tf2
- Updated publishers, subscribers, and services to ROS 2 style
- Replaced `ros::Time` with `rclcpp::Time`
- Updated parameter handling to ROS 2 style

### 3. Launch Files Migration
- Converted all XML launch files (`.launch`) to Python launch files (`.launch.py`)
- Updated all launch file syntax to ROS 2 format

### 4. Package Structure
- **hector_nav_msgs**: Message and service definitions
- **hector_marker_drawing**: Visualization marker drawing utilities
- **hector_map_tools**: Map processing utilities
- **hector_mapping**: Core SLAM node
- **hector_trajectory_server**: Trajectory recording and visualization
- **hector_map_server**: Map server for serving occupancy grids
- **hector_imu_attitude_to_tf**: IMU attitude to TF conversion
- **hector_imu_tools**: IMU utility tools
- **hector_geotiff**: GeoTIFF map generation
- **hector_geotiff_plugins**: GeoTIFF plugins
- **hector_compressed_map_transport**: Compressed map transport
- **hector_slam_launch**: Launch files for SLAM
- **hector_geotiff_launch**: Launch files for GeoTIFF generation

## Building

1. Source your ROS 2 Humble environment:
   ```bash
   source /opt/ros/humble/setup.bash
   ```

2. Build the workspace:
   ```bash
   cd ~/workfiles/cali_ws
   colcon build
   ```

3. Source the workspace:
   ```bash
   source install/setup.bash
   ```

## Launching Hector SLAM

### Basic SLAM Mapping

Launch the basic SLAM node with default parameters:

```bash
ros2 launch hector_mapping mapping_default.launch.py
```

This will start the hector_mapping node subscribing to the 'scan' topic.

### With Custom Parameters

```bash
ros2 launch hector_mapping mapping_default.launch.py \
  scan_topic:=/laser/scan \
  base_frame:=base_link \
  odom_frame:=odom \
  map_size:=4096
```

### Tutorial Launch (with GeoTIFF and RViz)

```bash
ros2 launch hector_slam_launch tutorial.launch.py
```

This launches:
- hector_mapping node
- hector_geotiff node for map saving
- RViz for visualization

### With Custom GeoTIFF Path

```bash
ros2 launch hector_slam_launch tutorial.launch.py \
  geotiff_map_file_path:=/path/to/maps
```

## Launch File Parameters

### mapping_default.launch.py Parameters

- `scan_topic` (default: `'scan'`): Laser scan topic name
- `base_frame` (default: `'base_footprint'`): Base frame name
- `odom_frame` (default: `'nav'`): Odometry frame name
- `pub_map_odom_transform` (default: `'true'`): Publish map to odom transform
- `map_size` (default: `'2048'`): Map size in pixels
- `map_resolution` (default: `0.050`): Map resolution in meters/pixel
- `map_update_distance_thresh` (default: `0.4`): Distance threshold for map updates
- `map_update_angle_thresh` (default: `0.06`): Angle threshold for map updates

### tutorial.launch.py Parameters

- `geotiff_map_file_path`: Path where GeoTIFF maps will be saved
- `use_sim_time` (default: `'true'`): Use simulation time

## Usage Examples

### 1. Basic SLAM with Laser Scanner

**Terminal 1**: Start SLAM
```bash
ros2 launch hector_mapping mapping_default.launch.py
```

**Terminal 2**: View map
```bash
ros2 run rviz2 rviz2
```

In RViz, add:
- Map display (topic: `/map`)
- LaserScan display (topic: `/scan`)
- TF display

### 2. SLAM with Trajectory Recording

```bash
ros2 launch hector_slam_launch tutorial.launch.py
```

This will:
- Start SLAM mapping
- Record robot trajectory
- Save GeoTIFF maps periodically
- Display everything in RViz

### 3. Using Map Server

**Terminal 1**: Start map server
```bash
ros2 run hector_map_server hector_map_server
```

**Terminal 2**: Get map via service
```bash
ros2 service call /map nav_msgs/srv/GetMap
```

### 4. IMU Attitude to TF

```bash
ros2 launch hector_imu_attitude_to_tf example.launch.py
```

## Topics

### Published Topics

- `/map` (`nav_msgs/msg/OccupancyGrid`): Occupancy grid map
- `/map_metadata` (`nav_msgs/msg/MapMetaData`): Map metadata
- `/slam_out_pose` (`geometry_msgs/msg/PoseStamped`): SLAM pose estimate
- `/tf` (`tf2_msgs/msg/TFMessage`): Transform tree

### Subscribed Topics

- `/scan` (`sensor_msgs/msg/LaserScan`): Laser scan data
- `/syscommand` (`std_msgs/msg/String`): System commands

### Services

- `/dynamic_map` (`nav_msgs/srv/GetMap`): Get current map
- `/reset_map` (`std_srvs/srv/Trigger`): Reset the map
- `/restart_hector` (`hector_mapping/srv/ResetMapping`): Restart SLAM with new pose
- `/pause_mapping` (`std_srvs/srv/SetBool`): Pause/resume mapping

## Frames

The typical frame structure is:
- `map`: Global map frame
- `scanmatcher_frame`: SLAM estimate frame
- `base_frame`: Robot base frame (e.g., `base_link`, `base_footprint`)
- `odom_frame`: Odometry frame (e.g., `odom`, `nav`)
- `laser_frame`: Laser scanner frame

## Configuration Tips

### 1. Map Size
- Larger maps (4096, 8192) provide more coverage but use more memory
- Smaller maps (1024, 2048) are faster but have limited range

### 2. Map Resolution
- `0.025 m/pixel`: High resolution, good for detailed mapping
- `0.050 m/pixel`: Default, good balance
- `0.100 m/pixel`: Lower resolution, faster processing

### 3. Update Thresholds
- `map_update_distance_thresh`: How far robot must move before map update
- `map_update_angle_thresh`: How much robot must rotate before map update
- Lower values = more frequent updates but more computation

### 4. Laser Parameters
- `laser_z_min_value` / `laser_z_max_value`: Filter laser points by height
- Useful for removing ground or ceiling reflections

## Troubleshooting

### 1. No map being published
- Check that `/scan` topic is being published: `ros2 topic list`
- Verify laser frame exists in TF: `ros2 run tf2_ros tf2_echo map base_link`
- Check node logs: `ros2 topic echo /rosout`

### 2. Map looks incorrect
- Verify TF tree is correct: `ros2 run tf2_tools view_frames`
- Check laser scan data: `ros2 topic echo /scan`
- Adjust `map_update_distance_thresh` and `map_update_angle_thresh`

### 3. Transform errors
- Ensure all frames are being published
- Check frame names match in launch file parameters
- Verify `base_frame` and `odom_frame` are correct

### 4. GeoTIFF not saving
- Check `geotiff_map_file_path` exists and is writable
- Verify `hector_trajectory_server` is running
- Check `geotiff_node` logs

## Additional Resources

- [ROS 2 Documentation](https://docs.ros.org/en/humble/)
- [Hector SLAM ROS 1 Wiki](http://wiki.ros.org/hector_slam)
- [TF2 Documentation](https://docs.ros.org/en/humble/Tutorials/Intermediate/Tf2.html)

## License

BSD License - See individual package LICENSE files for details.

## Maintainers

**Original ROS 1 version maintained by:**
- Johannes Meyer (meyer@fsr.tu-darmstadt.de)
- Stefan Kohlbrecher (kohlbrecher@sim.tu-darmstadt.de)

**ROS 2 Humble migration completed 2024.**
