//=================================================================================================
// Copyright (c) 2012, Stefan Kohlbrecher, TU Darmstadt
// All rights reserved.

// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the Simulation, Systems Optimization and Robotics
//       group, TU Darmstadt nor the names of its contributors may be used to
//       endorse or promote products derived from this software without
//       specific prior written permission.

// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//=================================================================================================


#include <rclcpp/rclcpp.hpp>
#include <tf2_ros/transform_broadcaster.h>
#include <sensor_msgs/msg/imu.hpp>
#include <geometry_msgs/msg/pose_stamped.hpp>
#include <nav_msgs/msg/odometry.hpp>
#include <tf2/utils.h>
#include <tf2/LinearMath/Quaternion.h>
#include <tf2/LinearMath/Matrix3x3.h>
#include <tf2_geometry_msgs/tf2_geometry_msgs.hpp>

class PoseAndOrientationToImuNode : public rclcpp::Node
{
public:
  PoseAndOrientationToImuNode()
  : Node("pose_and_orientation_to_imu_node")
  {
    // Declare and get parameters
    this->declare_parameter<std::string>("map_frame", "map");
    this->declare_parameter<std::string>("base_footprint_frame", "base_footprint");
    this->declare_parameter<std::string>("base_stabilized_frame", "base_stabilized");
    this->declare_parameter<std::string>("base_frame", "base_link");

    this->get_parameter("map_frame", p_map_frame_);
    this->get_parameter("base_footprint_frame", p_base_footprint_frame_);
    this->get_parameter("base_stabilized_frame", p_base_stabilized_frame_);
    this->get_parameter("base_frame", p_base_frame_);

    fused_imu_msg_.header.frame_id = p_base_stabilized_frame_;
    odom_msg_.header.frame_id = "map";

    tf_broadcaster_ = std::make_unique<tf2_ros::TransformBroadcaster>(*this);

    fused_imu_publisher_ = this->create_publisher<sensor_msgs::msg::Imu>("/fused_imu", 1);
    odometry_publisher_ = this->create_publisher<nav_msgs::msg::Odometry>("/state", 1);

    imu_subscriber_ = this->create_subscription<sensor_msgs::msg::Imu>(
      "/imu", 10, std::bind(&PoseAndOrientationToImuNode::imuMsgCallback, this, std::placeholders::_1));
    pose_subscriber_ = this->create_subscription<geometry_msgs::msg::PoseStamped>(
      "/pose", 10, std::bind(&PoseAndOrientationToImuNode::poseMsgCallback, this, std::placeholders::_1));

    callback_count_ = 0;
  }

private:
  void imuMsgCallback(const sensor_msgs::msg::Imu::SharedPtr imu_msg)
  {
    callback_count_++;

    tf2::Quaternion tmp;
    tf2::fromMsg(imu_msg->orientation, tmp);

    double imu_yaw, imu_pitch, imu_roll;
    tf2::Matrix3x3(tmp).getRPY(imu_roll, imu_pitch, imu_yaw);

    tf2::Transform transform;
    transform.setIdentity();
    tf2::Quaternion quat;

    quat.setRPY(imu_roll, imu_pitch, 0.0);

    // Publish transform from base_stabilized to base_frame
    geometry_msgs::msg::TransformStamped transform_stamped;
    transform_stamped.header.stamp = imu_msg->header.stamp;
    transform_stamped.header.frame_id = p_base_stabilized_frame_;
    transform_stamped.child_frame_id = p_base_frame_;
    transform_stamped.transform.translation.x = 0.0;
    transform_stamped.transform.translation.y = 0.0;
    transform_stamped.transform.translation.z = 0.0;
    transform_stamped.transform.rotation.x = quat.x();
    transform_stamped.transform.rotation.y = quat.y();
    transform_stamped.transform.rotation.z = quat.z();
    transform_stamped.transform.rotation.w = quat.w();
    tf_broadcaster_->sendTransform(transform_stamped);

    double pose_yaw = 0.0;

    if (last_pose_msg_ != nullptr){
      tf2::Quaternion pose_quat;
      tf2::fromMsg(last_pose_msg_->pose.orientation, pose_quat);
      double pose_pitch, pose_roll;
      tf2::Matrix3x3(pose_quat).getRPY(pose_roll, pose_pitch, pose_yaw);
    }

    orientation_quaternion_.setRPY(imu_roll, imu_pitch, pose_yaw);

    fused_imu_msg_.header.stamp = imu_msg->header.stamp;
    fused_imu_msg_.orientation.x = orientation_quaternion_.x();
    fused_imu_msg_.orientation.y = orientation_quaternion_.y();
    fused_imu_msg_.orientation.z = orientation_quaternion_.z();
    fused_imu_msg_.orientation.w = orientation_quaternion_.w();

    fused_imu_publisher_->publish(fused_imu_msg_);

    //If no pose message received, yaw is set to 0.
    //@TODO: Check for timestamp of pose and disable sending if too old
    if (last_pose_msg_ != nullptr){
      if ( (callback_count_ % 5) == 0){
        odom_msg_.header.stamp = imu_msg->header.stamp;
        odom_msg_.pose.pose.orientation = fused_imu_msg_.orientation;
        odom_msg_.pose.pose.position = last_pose_msg_->pose.position;

        odometry_publisher_->publish(odom_msg_);
      }
    }
  }

  void poseMsgCallback(const geometry_msgs::msg::PoseStamped::SharedPtr pose_msg)
  {
    tf2::Quaternion robot_pose_quaternion;
    tf2::fromMsg(pose_msg->pose.orientation, robot_pose_quaternion);
    
    tf2::Vector3 robot_pose_position(
      pose_msg->pose.position.x,
      pose_msg->pose.position.y,
      pose_msg->pose.position.z
    );

    tf2::Transform robot_pose_transform;
    robot_pose_transform.setRotation(robot_pose_quaternion);
    robot_pose_transform.setOrigin(robot_pose_position);

    tf2::Transform height_transform;
    height_transform.setIdentity();
    height_transform.setOrigin(tf2::Vector3(0.0, 0.0, 0.0));

    // Publish map to base_footprint transform
    geometry_msgs::msg::TransformStamped transform1;
    transform1.header.stamp = pose_msg->header.stamp;
    transform1.header.frame_id = p_map_frame_;
    transform1.child_frame_id = p_base_footprint_frame_;
    transform1.transform.translation.x = robot_pose_position.x();
    transform1.transform.translation.y = robot_pose_position.y();
    transform1.transform.translation.z = robot_pose_position.z();
    transform1.transform.rotation.x = robot_pose_quaternion.x();
    transform1.transform.rotation.y = robot_pose_quaternion.y();
    transform1.transform.rotation.z = robot_pose_quaternion.z();
    transform1.transform.rotation.w = robot_pose_quaternion.w();
    tf_broadcaster_->sendTransform(transform1);

    // Publish base_footprint to base_stabilized transform
    geometry_msgs::msg::TransformStamped transform2;
    transform2.header.stamp = pose_msg->header.stamp;
    transform2.header.frame_id = p_base_footprint_frame_;
    transform2.child_frame_id = p_base_stabilized_frame_;
    transform2.transform.translation.x = 0.0;
    transform2.transform.translation.y = 0.0;
    transform2.transform.translation.z = 0.0;
    transform2.transform.rotation.x = 0.0;
    transform2.transform.rotation.y = 0.0;
    transform2.transform.rotation.z = 0.0;
    transform2.transform.rotation.w = 1.0;
    tf_broadcaster_->sendTransform(transform2);

    // Perform simple estimation of vehicle altitude based on orientation
    if (last_pose_msg_ != nullptr){
      tf2::Matrix3x3 orientation_matrix(orientation_quaternion_);
      tf2::Vector3 plane_normal = orientation_matrix * tf2::Vector3(0.0, 0.0, 1.0);

      tf2::Vector3 last_position(
        last_pose_msg_->pose.position.x,
        last_pose_msg_->pose.position.y,
        last_pose_msg_->pose.position.z
      );

      // Calculate height difference (currently unused)
      double height_difference =
          (-plane_normal.x() * (robot_pose_position.x() - last_position.x())
           -plane_normal.y() * (robot_pose_position.y() - last_position.y())
           +plane_normal.z() * last_position.z()) / last_position.z();
      (void)height_difference;  // Suppress unused variable warning
    }

    last_pose_msg_ = pose_msg;
  }

  std::string p_map_frame_;
  std::string p_base_footprint_frame_;
  std::string p_base_stabilized_frame_;
  std::string p_base_frame_;
  std::unique_ptr<tf2_ros::TransformBroadcaster> tf_broadcaster_;

  tf2::Quaternion orientation_quaternion_;

  sensor_msgs::msg::Imu fused_imu_msg_;
  nav_msgs::msg::Odometry odom_msg_;
  geometry_msgs::msg::PoseStamped::SharedPtr last_pose_msg_;

  rclcpp::Publisher<sensor_msgs::msg::Imu>::SharedPtr fused_imu_publisher_;
  rclcpp::Publisher<nav_msgs::msg::Odometry>::SharedPtr odometry_publisher_;

  rclcpp::Subscription<sensor_msgs::msg::Imu>::SharedPtr imu_subscriber_;
  rclcpp::Subscription<geometry_msgs::msg::PoseStamped>::SharedPtr pose_subscriber_;

  size_t callback_count_;
};

int main(int argc, char **argv) {
  rclcpp::init(argc, argv);

  auto node = std::make_shared<PoseAndOrientationToImuNode>();

  rclcpp::spin(node);

  rclcpp::shutdown();

  return 0;
}
