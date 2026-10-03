//=================================================================================================
// Copyright (c) 2012, Gregor Gebhardt, TU Darmstadt
// All rights reserved.

// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the Flight Systems and Automatic Control group,
//       TU Darmstadt, nor the names of its contributors may be used to
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

#include <hector_geotiff/map_writer_interface.h>
#include <hector_geotiff/map_writer_plugin_interface.h>

#include <rclcpp/rclcpp.hpp>
#include <hector_nav_msgs/srv/get_robot_trajectory.hpp>

#include <fstream>
#include <memory>

namespace hector_geotiff_plugins
{

using namespace hector_geotiff;

class TrajectoryMapWriter : public MapWriterPluginInterface
{
public:
  TrajectoryMapWriter();
  virtual ~TrajectoryMapWriter();

  virtual void initialize(const std::string& name);
  virtual void draw(MapWriterInterface *interface);

protected:
  rclcpp::Node::SharedPtr node_;
  rclcpp::Client<hector_nav_msgs::srv::GetRobotTrajectory>::SharedPtr service_client_;

  bool initialized_;
  std::string name_;
  bool draw_all_objects_;
  std::string class_id_;
  int path_color_r_;
  int path_color_g_;
  int path_color_b_;
};

TrajectoryMapWriter::TrajectoryMapWriter()
    : node_(nullptr)
    , initialized_(false)
{}

TrajectoryMapWriter::~TrajectoryMapWriter()
{}

void TrajectoryMapWriter::initialize(const std::string& name)
{
  // Create a node for this plugin if it doesn't exist
  if (!node_) {
    node_ = rclcpp::Node::make_shared("trajectory_map_writer_" + name);
  }

  std::string service_name_;

  // Declare and get parameters
  node_->declare_parameter<std::string>(name + ".service_name", "trajectory");
  node_->declare_parameter<int>(name + ".path_color_r", 120);
  node_->declare_parameter<int>(name + ".path_color_g", 0);
  node_->declare_parameter<int>(name + ".path_color_b", 240);

  node_->get_parameter(name + ".service_name", service_name_);
  node_->get_parameter(name + ".path_color_r", path_color_r_);
  node_->get_parameter(name + ".path_color_g", path_color_g_);
  node_->get_parameter(name + ".path_color_b", path_color_b_);

  service_client_ = node_->create_client<hector_nav_msgs::srv::GetRobotTrajectory>(service_name_);

  initialized_ = true;
  this->name_ = name;
  RCLCPP_INFO(node_->get_logger(), "Successfully initialized hector_geotiff MapWriter plugin %s.", name_.c_str());
}

void TrajectoryMapWriter::draw(MapWriterInterface *interface)
{
    if(!initialized_) return;

    if (!service_client_->wait_for_service(std::chrono::seconds(1))) {
      RCLCPP_ERROR(node_->get_logger(), "Cannot draw trajectory, service %s not available", service_client_->get_service_name());
      return;
    }

    auto request = std::make_shared<hector_nav_msgs::srv::GetRobotTrajectory::Request>();
    auto result = service_client_->async_send_request(request);

    // Wait for the result with a timeout
    auto status = result.wait_for(std::chrono::seconds(1));
    if (status != std::future_status::ready) {
      RCLCPP_ERROR(node_->get_logger(), "Cannot draw trajectory, service %s timed out", service_client_->get_service_name());
      return;
    }
    
    auto response = result.get();
    if (!response) {
      RCLCPP_ERROR(node_->get_logger(), "Cannot draw trajectory, service %s failed", service_client_->get_service_name());
      return;
    }

    std::vector<geometry_msgs::msg::PoseStamped>& traj_vector (response->trajectory.poses);

    size_t size = traj_vector.size();

    std::vector<Eigen::Vector2f> pointVec;
    pointVec.resize(size);

    for (size_t i = 0; i < size; ++i){
      const geometry_msgs::msg::PoseStamped& pose (traj_vector[i]);

      pointVec[i] = Eigen::Vector2f(pose.pose.position.x, pose.pose.position.y);
    }

    if (size > 0){
      Eigen::Vector3f startVec(pointVec[0].x(),pointVec[0].y(),0.0f);
      interface->drawPath(startVec, pointVec, path_color_r_, path_color_g_, path_color_b_);
    }
}

} // namespace

//register this planner as a MapWriterPluginInterface plugin
#include <pluginlib/class_list_macros.hpp>
PLUGINLIB_EXPORT_CLASS(hector_geotiff_plugins::TrajectoryMapWriter, hector_geotiff::MapWriterPluginInterface)
