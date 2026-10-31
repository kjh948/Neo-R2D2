# MPU6050 - For Robot Operating System 2 (ROS2)

## Overview

This repository contains the MPU6050 package dedicated for ROS2. It contains the driver and node to allow integration with [sensor_msgs/msg/Imu Message](https://docs.ros2.org/foxy/api/sensor_msgs/msg/Imu.html)

## Hardware

This package has been tested with the following hardwares: \

Raspberry Pi 4B, 4GB RAM. \
MPU6050 sensor breakout board used: https://components101.com/sensors/mpu6050-module \
The Raspberry Pi 4B runs this image: https://github.com/ros-realtime/ros-realtime-rpi4-image

### Preferred Environment Setup

To run this example without issues, the following environment setup are preferred.

|                  |                          |
|------------------|--------------------------|
| Operating System | ros-realtime-rpi4-image  |
| ROS2 Version     | ROS2 Humble              |

### Package Integration

**Just clone this repository on your ros2 workspace/src.

### Sensor Calibration

The following steps provides detailed info to calibrate the sensor if necessary:

1. Build the package
```bash
colcon build --packages-select ros2_mpu6050
```
2. RPi to MPU6050 I2C wiring 
3. Place the sensor in a surface perpendicular to the direction of the gravitational acceleration. (**This is important)
4. Run the following command
```bash
ros2 run ros2_mpu6050 ros2_mpu6050_calibrate
```
5. The values of the offsets should be displayed
```bash
I2c communication started. .
MPU6050 initialization successful

**** Starting calibration ****
Ensure that the mpu6050 board is positioned in a surface perpendicular to the direction gravitational accelleration
This may take a while depending upon the no of samples, please wait. . .


In the params.yaml file under config directory, copy the following results accordingly

Gyroscope Offsets: 
gyro_x_offset --> -1.44153
gyro_y_offset --> 0.596412
gyro_z_offset --> 0.618626

Accelerometer Offsets: 
accel_x_offset --> 10.601
accel_y_offset --> -0.424632
accel_z_offset --> -3.21498
```
6. Copy these values in the params.yaml file under config directory of the package replacing the 0 values
```bash
# [deg/s]
gyro_x_offset: 0.0
gyro_y_offset: 0.0
gyro_z_offset: 0.0
# [m/s²]
accel_x_offset: 0.0
accel_y_offset: 0.0
accel_z_offset: 0.0
```

## Starting the ros2_mpu6050 node

Build the package
```bash
colcon build --packages-select ros2_mpu6050
```
and execute the following command.

```bash
ros2 launch ros2_mpu6050 ros2_mpu6050.launch.py
```
Build specific package

```bash
colcon build --packages-select `name_of_package`
```

## Listening to the IMU topic

After launching ros2_mpu6050.launch.py,

In the host PC or in the RPi SSH client terminal, does not matter, execute the following command

```bash
ros2 topic echo /imu/mpu6050
```

If the above command does not work, ensure that the /imu/mpu6050 topic shows when running the following command

```bash
ros2 topic list
```

## Inspecting IMU data for fusion

The `analyze_imu` command subscribes to the IMU topic and reports the observed
frame, timestamp rate/jitter, axis means and sample variances, acceleration
magnitude, and covariance currently carried by the messages:

```bash
ros2 run ros2_mpu6050 analyze_imu --duration 15 --expected-frame base_link
```

Keep the robot completely still while collecting samples. To also print
candidate diagonal variance values for the driver parameters, explicitly
attest that the sensor remained stationary:

```bash
ros2 run ros2_mpu6050 analyze_imu --duration 30 --expected-frame base_link --stationary
```

The candidate values are observed sample variances, not a complete noise or
bias model. Repeat the measurement and validate the values before using them
for EKF tuning. The tool returns a non-zero status if no samples arrive, the
expected frame differs, timestamps are not increasing, or the measured rate
is below `--min-rate` (default 80 Hz).

The driver publishes orientation as unavailable (`orientation_covariance[0] =
-1`). Its `imu_frame` parameter is now applied to `header.frame_id`; if the IMU
is mounted with a different orientation from the robot axes, use a dedicated
frame such as `imu_link` and publish the measured static transform to
`base_link`. Do not change only the frame name to compensate for rotated axes.

The angular-velocity and acceleration variance parameters in `config/params.yaml`
default to zero until measured. After collecting stationary samples, copy the
reported `gyro_{x,y,z}_variance` and `accel_{x,y,z}_variance` values into the
same-named parameters. Units are `(rad/s)^2` and `(m/s^2)^2`, respectively.
The existing calibration executable estimates offsets by averaging samples;
it does not estimate covariance or a complete accelerometer gravity model.
