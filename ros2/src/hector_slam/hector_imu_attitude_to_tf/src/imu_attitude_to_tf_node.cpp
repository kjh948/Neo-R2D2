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
#include <tf2/utils.h>
#include <tf2/LinearMath/Quaternion.h>
#include <tf2/LinearMath/Matrix3x3.h>
#include <tf2_geometry_msgs/tf2_geometry_msgs.hpp>

class ImuAttitudeToTfNode : public rclcpp::Node
{
public:
  ImuAttitudeToTfNode()
  : Node("imu_attitude_to_tf_node")
  {
    // Declare and get parameters
    this->declare_parameter<std::string>("base_stabilized_frame", "base_stabilized");
    this->declare_parameter<std::string>("base_frame", "base_link");
    this->declare_parameter<std::string>("imu_topic", "imu_topic");

    this->get_parameter("base_stabilized_frame", p_base_stabilized_frame_);
    this->get_parameter("base_frame", p_base_frame_);
    
    std::string imu_topic;
    this->get_parameter("imu_topic", imu_topic);

    // Initialize transform broadcaster
    tf_broadcaster_ = std::make_unique<tf2_ros::TransformBroadcaster>(*this);

    // Initialize transform
    transform_.header.frame_id = p_base_stabilized_frame_;
    transform_.child_frame_id = p_base_frame_;
    transform_.transform.translation.x = 0.0;
    transform_.transform.translation.y = 0.0;
    transform_.transform.translation.z = 0.0;

    // Create subscriber
    imu_subscriber_ = this->create_subscription<sensor_msgs::msg::Imu>(
      imu_topic, 10, std::bind(&ImuAttitudeToTfNode::imuMsgCallback, this, std::placeholders::_1));
  }

private:
  void imuMsgCallback(const sensor_msgs::msg::Imu::SharedPtr imu_msg)
  {
    tf2::Quaternion tmp;
    tf2::fromMsg(imu_msg->orientation, tmp);

    double roll, pitch, yaw;
    tf2::Matrix3x3(tmp).getRPY(roll, pitch, yaw);

    tmp.setRPY(roll, pitch, 0.0);

    transform_.header.stamp = imu_msg->header.stamp;
    transform_.transform.rotation.x = tmp.x();
    transform_.transform.rotation.y = tmp.y();
    transform_.transform.rotation.z = tmp.z();
    transform_.transform.rotation.w = tmp.w();

    tf_broadcaster_->sendTransform(transform_);
  }

  std::string p_base_stabilized_frame_;
  std::string p_base_frame_;
  std::unique_ptr<tf2_ros::TransformBroadcaster> tf_broadcaster_;
  geometry_msgs::msg::TransformStamped transform_;
  rclcpp::Subscription<sensor_msgs::msg::Imu>::SharedPtr imu_subscriber_;
};

int main(int argc, char **argv) {
  rclcpp::init(argc, argv);

  auto node = std::make_shared<ImuAttitudeToTfNode>();

  rclcpp::spin(node);

  rclcpp::shutdown();

  return 0;
}
