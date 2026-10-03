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

#include "hector_geotiff/geotiff_writer.h"
#include "hector_geotiff/map_writer_plugin_interface.h"

#include <cstdio>
#include <rclcpp/rclcpp.hpp>

#include <pluginlib/class_loader.hpp>

#include <memory>
#include <boost/algorithm/string.hpp>

#include <geometry_msgs/msg/quaternion.hpp>
#include <nav_msgs/srv/get_map.hpp>
#include <std_msgs/msg/string.hpp>
#include <hector_nav_msgs/srv/get_robot_trajectory.hpp>

#include <QApplication>

using namespace std;

namespace hector_geotiff{

/**
 * @brief Map generation node.
 */
class MapGenerator : public rclcpp::Node
{
public:
  MapGenerator()
  : Node("geotiff_node")
  , geotiff_writer_(false)
  , running_saved_map_num_(0)
  {
    // Declare and get parameters
    this->declare_parameter<std::string>("map_file_path", ".");
    this->declare_parameter<std::string>("map_file_base_name", "");
    this->declare_parameter<bool>("draw_background_checkerboard", true);
    this->declare_parameter<bool>("draw_free_space_grid", true);
    this->declare_parameter<bool>("use_map_topic", false);
    this->declare_parameter<double>("geotiff_save_period", 0.0);
    this->declare_parameter<std::string>("plugins", "");

    this->get_parameter("map_file_path", p_map_file_path_);
    geotiff_writer_.setMapFilePath(p_map_file_path_);
    geotiff_writer_.setUseUtcTimeSuffix(true);

    this->get_parameter("map_file_base_name", p_map_file_base_name_);
    this->get_parameter("draw_background_checkerboard", p_draw_background_checkerboard_);
    this->get_parameter("draw_free_space_grid", p_draw_free_space_grid_);
    this->get_parameter("use_map_topic", use_map_topic_);
    this->get_parameter("plugins", p_plugin_list_);

    sys_cmd_sub_ = this->create_subscription<std_msgs::msg::String>(
      "syscommand", 1, std::bind(&MapGenerator::sysCmdCallback, this, std::placeholders::_1));

    if(!use_map_topic_) {
      map_service_client_ = this->create_client<nav_msgs::srv::GetMap>("map");
    }
    path_service_client_ = this->create_client<hector_nav_msgs::srv::GetRobotTrajectory>("trajectory");

    double p_geotiff_save_period = 0.0;
    this->get_parameter("geotiff_save_period", p_geotiff_save_period);

    if(p_geotiff_save_period > 0.0){
      map_save_timer_ = this->create_wall_timer(
        std::chrono::milliseconds(static_cast<int>(p_geotiff_save_period * 1000.0)),
        std::bind(&MapGenerator::timerSaveGeotiffCallback, this));
    }

    std::vector<std::string> plugin_list;
    boost::algorithm::split(plugin_list, p_plugin_list_, boost::is_any_of("\t "));

    //We always have at least one element containing "" in the string list
    if ((plugin_list.size() > 0) && (plugin_list[0].length() > 0)){
      plugin_loader_ = std::make_unique<pluginlib::ClassLoader<hector_geotiff::MapWriterPluginInterface>>("hector_geotiff", "hector_geotiff::MapWriterPluginInterface");

      for (size_t i = 0; i < plugin_list.size(); ++i){
        try
        {
          std::shared_ptr<hector_geotiff::MapWriterPluginInterface> tmp (plugin_loader_->createSharedInstance(plugin_list[i]));
          tmp->initialize(plugin_loader_->getName(plugin_list[i]));
          plugin_vector_.push_back(tmp);
        }
        catch(pluginlib::PluginlibException& ex)
        {
          RCLCPP_ERROR(this->get_logger(), "The plugin failed to load for some reason. Error: %s", ex.what());
        }
      }
    }else{
      RCLCPP_INFO(this->get_logger(), "No plugins loaded for geotiff node");
    }

    RCLCPP_INFO(this->get_logger(), "Geotiff node started");
  }

  ~MapGenerator() = default;

  void writeGeotiff(bool completed)
  {
    rclcpp::Time start_time = this->now();

    bool received_map = false;
    nav_msgs::msg::OccupancyGrid::SharedPtr map;

    if (use_map_topic_)
    {
      // Wait for map message (simplified - in production, use a proper message filter)
      RCLCPP_WARN(this->get_logger(), "use_map_topic mode not fully implemented in ROS2 migration");
      return;
    }
    else
    {
      if (!map_service_client_->wait_for_service(std::chrono::seconds(4))) {
        RCLCPP_ERROR(this->get_logger(), "Map service not available");
        return;
      }

      auto request = std::make_shared<nav_msgs::srv::GetMap::Request>();
      auto result = map_service_client_->async_send_request(request);
      
      if (rclcpp::spin_until_future_complete(this->shared_from_this(), result) == rclcpp::FutureReturnCode::SUCCESS)
      {
        map = std::make_shared<nav_msgs::msg::OccupancyGrid>(result.get()->map);
        received_map = true;
      }
    }

    if (received_map)
    {
      RCLCPP_INFO(this->get_logger(), "GeotiffNode: Map service called successfully");

      std::string map_file_name = p_map_file_base_name_;
      std::string competition_name;
      std::string team_name;
      std::string mission_name;
      std::string postfix;
      
      // Get parameters (ROS2 style)
      this->declare_parameter<std::string>("/competition", "");
      this->declare_parameter<std::string>("/team", "");
      this->declare_parameter<std::string>("/mission", "");
      this->declare_parameter<std::string>("map_file_postfix", "");
      
      if (this->get_parameter("/competition", competition_name) && !competition_name.empty()) 
        map_file_name = map_file_name + "_" + competition_name;
      if (this->get_parameter("/team", team_name) && !team_name.empty()) 
        map_file_name = map_file_name + "_" + team_name;
      if (this->get_parameter("/mission", mission_name) && !mission_name.empty()) 
        map_file_name = map_file_name + "_" + mission_name;
      if (this->get_parameter("map_file_postfix", postfix) && !postfix.empty()) 
        map_file_name = map_file_name + "_" + postfix;
      
      if (map_file_name.substr(0, 1) == "_") map_file_name = map_file_name.substr(1);
      if (map_file_name.empty()) map_file_name = "GeoTiffMap";
      geotiff_writer_.setMapFileName(map_file_name);
      bool transformSuccess = geotiff_writer_.setupTransforms(*map);

      if(!transformSuccess){
        RCLCPP_INFO(this->get_logger(), "Couldn't set map transform");
        return;
      }

      geotiff_writer_.setupImageSize();

      if (p_draw_background_checkerboard_){
        geotiff_writer_.drawBackgroundCheckerboard();
      }

      geotiff_writer_.drawMap(*map, p_draw_free_space_grid_);
      geotiff_writer_.drawCoords();

      geotiff_writer_.completed_map_ = completed;
    }
    else
    {
      RCLCPP_ERROR(this->get_logger(), "Failed to call map service");
      return;
    }

    RCLCPP_INFO(this->get_logger(), "Writing geotiff plugins");
    for (size_t i = 0; i < plugin_vector_.size(); ++i){
      plugin_vector_[i]->draw(&geotiff_writer_);
    }

    RCLCPP_INFO(this->get_logger(), "Writing geotiff");

    geotiff_writer_.writeGeotiffImage(completed);
    running_saved_map_num_++;

    rclcpp::Duration elapsed_time = this->now() - start_time;

    RCLCPP_INFO(this->get_logger(), "GeoTiff created in %f seconds", elapsed_time.seconds());
  }

  void timerSaveGeotiffCallback()
  {
    this->writeGeotiff(false);
  }

  void sysCmdCallback(const std_msgs::msg::String::SharedPtr sys_cmd)
  {
    if (sys_cmd->data != "savegeotiff"){
      return;
    }

    this->writeGeotiff(true);
  }

  std::string p_map_file_path_;
  std::string p_map_file_base_name_;
  std::string p_plugin_list_;
  bool p_draw_background_checkerboard_;
  bool p_draw_free_space_grid_;
  bool use_map_topic_;

  rclcpp::Client<nav_msgs::srv::GetMap>::SharedPtr map_service_client_;
  rclcpp::Client<hector_nav_msgs::srv::GetRobotTrajectory>::SharedPtr path_service_client_;

  rclcpp::Subscription<std_msgs::msg::String>::SharedPtr sys_cmd_sub_;

  std::unique_ptr<pluginlib::ClassLoader<hector_geotiff::MapWriterPluginInterface>> plugin_loader_;
  std::vector<std::shared_ptr<hector_geotiff::MapWriterPluginInterface> > plugin_vector_;

  GeotiffWriter geotiff_writer_;

  rclcpp::TimerBase::SharedPtr map_save_timer_;

  unsigned int running_saved_map_num_;

  std::string start_dir_;
};

}

int main(int argc, char** argv)
{
  rclcpp::init(argc, argv);

  auto mg = std::make_shared<hector_geotiff::MapGenerator>();

  rclcpp::spin(mg);

  rclcpp::shutdown();

  return 0;
}
