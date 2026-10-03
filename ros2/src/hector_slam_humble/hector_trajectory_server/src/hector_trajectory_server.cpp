//=================================================================================================
// Copyright (c) 2011, Stefan Kohlbrecher, TU Darmstadt
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

#include <cstdio>
#include <rclcpp/rclcpp.hpp>

#include <nav_msgs/msg/path.hpp>
#include <std_msgs/msg/string.hpp>

#include <geometry_msgs/msg/quaternion.hpp>
#include <geometry_msgs/msg/pose_stamped.hpp>

#include <tf2_ros/transform_listener.h>
#include <tf2_ros/buffer.h>
#include <tf2_geometry_msgs/tf2_geometry_msgs.hpp>

#include <hector_nav_msgs/srv/get_robot_trajectory.hpp>
#include <hector_nav_msgs/srv/get_recovery_info.hpp>

#include <tf2/utils.h>

#include <algorithm>
#include <chrono>

using namespace std;

bool comparePoseStampedStamps (const geometry_msgs::msg::PoseStamped& t1, const geometry_msgs::msg::PoseStamped& t2) { 
  rclcpp::Time t1_time(t1.header.stamp);
  rclcpp::Time t2_time(t2.header.stamp);
  return (t1_time < t2_time); 
}


/**
 * @brief Map generation node.
 */
class PathContainer : public rclcpp::Node
{
public:
  PathContainer()
  : Node("hector_trajectory_server")
  {
    // Declare and get parameters
    this->declare_parameter<std::string>("target_frame_name", "map");
    this->declare_parameter<std::string>("source_frame_name", "base_link");
    this->declare_parameter<double>("trajectory_update_rate", 4.0);
    this->declare_parameter<double>("trajectory_publish_rate", 0.25);

    this->get_parameter("target_frame_name", p_target_frame_name_);
    this->get_parameter("source_frame_name", p_source_frame_name_);
    this->get_parameter("trajectory_update_rate", p_trajectory_update_rate_);
    this->get_parameter("trajectory_publish_rate", p_trajectory_publish_rate_);

    // Initialize TF
    tf_buffer_ = std::make_shared<tf2_ros::Buffer>(this->get_clock());
    tf_listener_ = std::make_shared<tf2_ros::TransformListener>(*tf_buffer_);

    waitForTf();

    sys_cmd_sub_ = this->create_subscription<std_msgs::msg::String>("syscommand", 1, 
      std::bind(&PathContainer::sysCmdCallback, this, std::placeholders::_1));
    trajectory_pub_ = this->create_publisher<nav_msgs::msg::Path>("trajectory", rclcpp::QoS(1).transient_local());

    trajectory_provider_service_ = this->create_service<hector_nav_msgs::srv::GetRobotTrajectory>("trajectory", 
      std::bind(&PathContainer::trajectoryProviderCallBack, this, std::placeholders::_1, std::placeholders::_2));
    recovery_info_provider_service_ = this->create_service<hector_nav_msgs::srv::GetRecoveryInfo>("trajectory_recovery_info", 
      std::bind(&PathContainer::recoveryInfoProviderCallBack, this, std::placeholders::_1, std::placeholders::_2));

    last_reset_time_ = this->now();

    update_trajectory_timer_ = this->create_wall_timer(
      std::chrono::milliseconds(static_cast<int>(1000.0 / p_trajectory_update_rate_)),
      std::bind(&PathContainer::trajectoryUpdateTimerCallback, this));
    publish_trajectory_timer_ = this->create_wall_timer(
      std::chrono::milliseconds(static_cast<int>(1000.0 / p_trajectory_publish_rate_)),
      std::bind(&PathContainer::publishTrajectoryTimerCallback, this));

    pose_source_.pose.orientation.w = 1.0;
    pose_source_.header.frame_id = p_source_frame_name_;

    trajectory_.header.frame_id = p_target_frame_name_;
  }

  void waitForTf()
  {
    rclcpp::Time start = this->now();
    RCLCPP_INFO(this->get_logger(), "Waiting for tf transform data between frames %s and %s to become available", 
                p_target_frame_name_.c_str(), p_source_frame_name_.c_str());

    bool transform_successful = false;

    while (!transform_successful && rclcpp::ok()){
      try {
        geometry_msgs::msg::TransformStamped transform = tf_buffer_->lookupTransform(
          p_target_frame_name_, p_source_frame_name_, tf2::TimePointZero);
        transform_successful = true;
        break;
      } catch (tf2::TransformException &ex) {
        // Transform not available yet
      }

      rclcpp::Time now = this->now();
      if ((now - start).seconds() > 20.0){
        RCLCPP_WARN_THROTTLE(this->get_logger(), *this->get_clock(), 10000,
          "No transform between frames %s and %s available after %f seconds of waiting.", 
          p_target_frame_name_.c_str(), p_source_frame_name_.c_str(), (now - start).seconds());
      }
      
      if (!rclcpp::ok()) return;
      rclcpp::sleep_for(std::chrono::seconds(1));
    }

    rclcpp::Time end = this->now();
    RCLCPP_INFO(this->get_logger(), "Finished waiting for tf, waited %f seconds", (end - start).seconds());
  }


  void sysCmdCallback(const std_msgs::msg::String::SharedPtr sys_cmd)
  {
    if (sys_cmd->data == "reset")
    {
    last_reset_time_ = this->now();
    trajectory_.poses.clear();
    trajectory_.header.stamp = this->now();
    }
  }

  void addCurrentTfPoseToTrajectory()
  {
    pose_source_.header.stamp = rclcpp::Time(0, 0, RCL_ROS_TIME);

    geometry_msgs::msg::PoseStamped pose_out;

    try {
      pose_out = tf_buffer_->transform(pose_source_, p_target_frame_name_);
    } catch (tf2::TransformException &ex) {
      RCLCPP_WARN(this->get_logger(), "Transform failed: %s", ex.what());
      return;
    }

    if (trajectory_.poses.size() != 0){
      //Only add pose to trajectory if it's not already stored
      rclcpp::Time last_stamp(trajectory_.poses.back().header.stamp);
      rclcpp::Time current_stamp(pose_out.header.stamp);
      if (current_stamp != last_stamp){
        trajectory_.poses.push_back(pose_out);
      }
    }else{
      trajectory_.poses.push_back(pose_out);
    }

    trajectory_.header.stamp = pose_out.header.stamp;
  }

  void trajectoryUpdateTimerCallback()
  {
    try{
      addCurrentTfPoseToTrajectory();
    }catch(tf2::TransformException &e)
    {
      RCLCPP_WARN(this->get_logger(), "Trajectory Server: Transform from %s to %s failed: %s", 
                  p_target_frame_name_.c_str(), pose_source_.header.frame_id.c_str(), e.what());
    }
  }

  void publishTrajectoryTimerCallback()
  {
    trajectory_pub_->publish(trajectory_);
  }

  void trajectoryProviderCallBack(const std::shared_ptr<hector_nav_msgs::srv::GetRobotTrajectory::Request> /* req */,
                                  std::shared_ptr<hector_nav_msgs::srv::GetRobotTrajectory::Response> res)
  {
    res->trajectory = trajectory_;
  }

  inline const nav_msgs::msg::Path getTrajectory() const
  {
    return trajectory_;
  }

  void recoveryInfoProviderCallBack(const std::shared_ptr<hector_nav_msgs::srv::GetRecoveryInfo::Request> req,
                                    std::shared_ptr<hector_nav_msgs::srv::GetRecoveryInfo::Response> res)
  {
    const rclcpp::Time req_time = rclcpp::Time(req->request_time);

    geometry_msgs::msg::PoseStamped tmp;
    tmp.header.stamp = req->request_time;

    std::vector<geometry_msgs::msg::PoseStamped> const & poses = trajectory_.poses;

    if(poses.size() == 0)
    {
        RCLCPP_WARN(this->get_logger(), "Failed to find trajectory leading out of radius %f"
                 " because no poses, i.e. no inverse trajectory, exists.", req->request_radius);
        return;
    }

    //Find the robot pose in the saved trajectory
    std::vector<geometry_msgs::msg::PoseStamped>::const_iterator it
            = std::lower_bound(poses.begin(), poses.end(), tmp, comparePoseStampedStamps);

    //If we didn't find the robot pose for the desired time, add the current robot pose to trajectory
    if (it == poses.end()){
      addCurrentTfPoseToTrajectory();
      it = poses.end();
      --it;
    }

    std::vector<geometry_msgs::msg::PoseStamped>::const_iterator it_start = it;

    const geometry_msgs::msg::Point& req_coords ((*it).pose.position);

    double dist_sqr_threshold = req->request_radius * req->request_radius;

    double dist_sqr = 0.0;

    //Iterate backwards till the start of the trajectory is reached or we find a pose that's outside the specified radius
    while (it != poses.begin() && dist_sqr < dist_sqr_threshold){
      const geometry_msgs::msg::Point& curr_coords ((*it).pose.position);

      dist_sqr = (req_coords.x - curr_coords.x) * (req_coords.x - curr_coords.x) +
                 (req_coords.y - curr_coords.y) * (req_coords.y - curr_coords.y);

      --it;
    }

    if (dist_sqr < dist_sqr_threshold){
      RCLCPP_INFO(this->get_logger(), "Failed to find trajectory leading out of radius %f", req->request_radius);
      return;
    }

    std::vector<geometry_msgs::msg::PoseStamped>::const_iterator it_end = it;

    res->req_pose = *it_start;
    res->radius_entry_pose = *it_end;

    std::vector<geometry_msgs::msg::PoseStamped>& traj_out_poses = res->trajectory_radius_entry_pose_to_req_pose.poses;

    res->trajectory_radius_entry_pose_to_req_pose.poses.clear();
    res->trajectory_radius_entry_pose_to_req_pose.header = res->req_pose.header;

    for (std::vector<geometry_msgs::msg::PoseStamped>::const_iterator it_tmp = it_start; it_tmp != it_end; --it_tmp){
      traj_out_poses.push_back(*it_tmp);
    }
  }

  //parameters
  std::string p_target_frame_name_;
  std::string p_source_frame_name_;
  double p_trajectory_update_rate_;
  double p_trajectory_publish_rate_;

  // Zero pose used for transformation to target_frame.
  geometry_msgs::msg::PoseStamped pose_source_;

  rclcpp::Service<hector_nav_msgs::srv::GetRobotTrajectory>::SharedPtr trajectory_provider_service_;
  rclcpp::Service<hector_nav_msgs::srv::GetRecoveryInfo>::SharedPtr recovery_info_provider_service_;

  rclcpp::TimerBase::SharedPtr update_trajectory_timer_;
  rclcpp::TimerBase::SharedPtr publish_trajectory_timer_;

  rclcpp::Subscription<std_msgs::msg::String>::SharedPtr sys_cmd_sub_;
  rclcpp::Publisher<nav_msgs::msg::Path>::SharedPtr trajectory_pub_;

  nav_msgs::msg::Path trajectory_;

  std::shared_ptr<tf2_ros::TransformListener> tf_listener_;
  std::shared_ptr<tf2_ros::Buffer> tf_buffer_;

  rclcpp::Time last_reset_time_;
  rclcpp::Time last_pose_save_time_;
};

int main(int argc, char** argv)
{
  rclcpp::init(argc, argv);

  auto node = std::make_shared<PathContainer>();

  rclcpp::spin(node);

  rclcpp::shutdown();

  return 0;
}
