//=================================================================================================
// Copyright (c) 2011, Stefan Kohlbrecher, Johannes Meyer TU Darmstadt
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

#include <stdio.h>
#include <stdlib.h>

#include <rclcpp/rclcpp.hpp>
#include <nav_msgs/msg/map_meta_data.hpp>
#include <nav_msgs/msg/occupancy_grid.hpp>
#include <nav_msgs/srv/get_map.hpp>

#include <tf2_ros/transform_listener.h>
#include <tf2_ros/buffer.h>
#include <tf2_geometry_msgs/tf2_geometry_msgs.hpp>
#include <tf2/utils.h>

#include <hector_map_tools/HectorMapTools.h>

#include <hector_nav_msgs/srv/get_distance_to_obstacle.hpp>
#include <hector_nav_msgs/srv/get_search_position.hpp>

#include <memory>
#include <chrono>

#include "hector_marker_drawing/HectorDrawings.h"


class OccupancyGridContainer
{
public:
  OccupancyGridContainer(std::string /* sub_topic */, std::string /* prefix */, rclcpp::Node::SharedPtr node, HectorDrawings* drawing_provider, std::shared_ptr<tf2_ros::Buffer> tf_buffer)
    : drawing_provider_(drawing_provider)
    , tf_buffer_(tf_buffer)
    , node_(node)
  {
    std::string service_name = "map";
    map_service_ = node_->create_service<nav_msgs::srv::GetMap>(service_name, 
      std::bind(&OccupancyGridContainer::mapServiceCallback, this, std::placeholders::_1, std::placeholders::_2));

    std::string lookup_service_name = "get_distance_to_obstacle";
    dist_lookup_service_ = node_->create_service<hector_nav_msgs::srv::GetDistanceToObstacle>(lookup_service_name, 
      std::bind(&OccupancyGridContainer::lookupServiceCallback, this, std::placeholders::_1, std::placeholders::_2));

    std::string get_search_pos_service_name = "get_search_position";
    get_search_pos_service_ = node_->create_service<hector_nav_msgs::srv::GetSearchPosition>(get_search_pos_service_name, 
      std::bind(&OccupancyGridContainer::getSearchPosServiceCallback, this, std::placeholders::_1, std::placeholders::_2));

    map_sub_ = node_->create_subscription<nav_msgs::msg::OccupancyGrid>("map", 1, 
      std::bind(&OccupancyGridContainer::mapCallback, this, std::placeholders::_1));
  }

  ~OccupancyGridContainer()
  {}

  void mapServiceCallback(const std::shared_ptr<nav_msgs::srv::GetMap::Request> /* req */,
                          std::shared_ptr<nav_msgs::srv::GetMap::Response> res)
  {
    RCLCPP_INFO(node_->get_logger(), "hector_map_server map service called");

    if (!map_ptr_){
      RCLCPP_INFO(node_->get_logger(), "map_server has no map yet, no map service available");
      return;
    }

    res->map = *map_ptr_;
  }

  void lookupServiceCallback(const std::shared_ptr<hector_nav_msgs::srv::GetDistanceToObstacle::Request> req,
                             std::shared_ptr<hector_nav_msgs::srv::GetDistanceToObstacle::Response> res)
  {
    if (!map_ptr_){
      RCLCPP_INFO(node_->get_logger(), "map_server has no map yet, no lookup service available");
      return;
    }

    try{
      geometry_msgs::msg::TransformStamped transform = tf_buffer_->lookupTransform(
        map_ptr_->header.frame_id, req->point.header.frame_id, req->point.header.stamp, rclcpp::Duration::from_seconds(1.0));

      // Transform point to map frame
      geometry_msgs::msg::PointStamped point_transformed;
      point_transformed.header = req->point.header;
      point_transformed.point = req->point.point;
      point_transformed = tf_buffer_->transform(point_transformed, map_ptr_->header.frame_id);

      // Get origin from transform
      Eigen::Vector2f start(transform.transform.translation.x, transform.transform.translation.y);
      Eigen::Vector2f end(point_transformed.point.x, point_transformed.point.y);

      Eigen::Vector2f hit_world;
      float dist = dist_meas_.getDist(start, end, &hit_world);

      if (dist >= 0.0f){
        Eigen::Vector2f diff = end - start;
        float diff_length = diff.norm();
        if (diff_length > 0.0f) {
          float angle = std::acos(std::max(-1.0f, std::min(1.0f, diff.x() / diff_length)));
          res->distance = dist / std::cos(angle);
        } else {
          res->distance = dist;
        }
      } else {
        res->distance = -1.0f;
      }

      // Debug drawing
      if (drawing_provider_){
        float cube_scale = map_ptr_->info.resolution;
        drawing_provider_->setColor(1.0, 0.0, 0.0);
        drawing_provider_->setScale(static_cast<double>(cube_scale));
        drawing_provider_->drawPoint(start);
        drawing_provider_->setColor(0.0, 1.0, 0.0);
        drawing_provider_->drawPoint(end);
        if (dist >= 0.0f){
          drawing_provider_->setColor(0.0, 0.0, 1.0);
          drawing_provider_->drawPoint(hit_world);
        }
        drawing_provider_->sendAndResetData();
      }
      return;

    } catch(tf2::TransformException &e)
    {
      RCLCPP_ERROR(node_->get_logger(), "Transform failed in lookup distance service call: %s", e.what());
    }

    res->distance = -1.0f;
  }

  void getSearchPosServiceCallback(const std::shared_ptr<hector_nav_msgs::srv::GetSearchPosition::Request> req,
                                   std::shared_ptr<hector_nav_msgs::srv::GetSearchPosition::Response> res)
  {
    if (!map_ptr_){
      RCLCPP_INFO(node_->get_logger(), "map_server has no map yet, no get best search pos service available");
      return;
    }

    try{
      geometry_msgs::msg::PoseStamped transformed_pose = tf_buffer_->transform(req->ooi_pose, map_ptr_->header.frame_id);

      // Calculate search position: move backwards by req->distance along the x-axis of the pose
      tf2::Transform pose_tf;
      tf2::fromMsg(transformed_pose.pose, pose_tf);

      tf2::Vector3 direction(-req->distance, 0.0, 0.0);
      tf2::Vector3 search_origin = pose_tf.getOrigin() + pose_tf.getBasis() * direction;

      pose_tf.setOrigin(search_origin);
      res->search_pose.header = transformed_pose.header;
      
      // Convert Transform to Pose
      geometry_msgs::msg::Pose search_pose;
      search_pose.position.x = pose_tf.getOrigin().x();
      search_pose.position.y = pose_tf.getOrigin().y();
      search_pose.position.z = pose_tf.getOrigin().z();
      tf2::Quaternion q = pose_tf.getRotation();
      search_pose.orientation = tf2::toMsg(q);
      res->search_pose.pose = search_pose;

      return;

    } catch(tf2::TransformException &e){
      RCLCPP_ERROR(node_->get_logger(), "Transform failed in getSearchPosition service call: %s", e.what());
    }

    res->search_pose = req->ooi_pose; // Return original pose on error
  }

  void mapCallback(const nav_msgs::msg::OccupancyGrid::SharedPtr map)
  {
    map_ptr_ = map;
    dist_meas_.setMap(map_ptr_);
  }

  //Services
  rclcpp::Service<nav_msgs::srv::GetMap>::SharedPtr map_service_;
  rclcpp::Service<hector_nav_msgs::srv::GetDistanceToObstacle>::SharedPtr dist_lookup_service_;
  rclcpp::Service<hector_nav_msgs::srv::GetSearchPosition>::SharedPtr get_search_pos_service_;

  //Subscriber
  rclcpp::Subscription<nav_msgs::msg::OccupancyGrid>::SharedPtr map_sub_;

  HectorMapTools::DistanceMeasurementProvider dist_meas_;

  HectorDrawings* drawing_provider_;
  std::shared_ptr<tf2_ros::Buffer> tf_buffer_;
  rclcpp::Node::SharedPtr node_;

  nav_msgs::msg::OccupancyGrid::SharedPtr map_ptr_;
};

class HectorMapServer : public rclcpp::Node
{
public:
    /** Trivial constructor */
    HectorMapServer()
    : Node("hector_map_server")
    {
      std::string frame_id;

      hector_drawings_ = new HectorDrawings(shared_from_this());
      hector_drawings_->setNamespace("map_server");

      tf_buffer_ = std::make_shared<tf2_ros::Buffer>(this->get_clock());
      tf_listener_ = std::make_shared<tf2_ros::TransformListener>(*tf_buffer_);

      mapContainer = new OccupancyGridContainer("map", "", shared_from_this(), hector_drawings_, tf_buffer_);
    }

    ~HectorMapServer()
    {
      delete mapContainer;
      if (hector_drawings_)
        delete hector_drawings_;
    }

public:
    OccupancyGridContainer* mapContainer;
private:
    HectorDrawings* hector_drawings_;
    std::shared_ptr<tf2_ros::TransformListener> tf_listener_;
    std::shared_ptr<tf2_ros::Buffer> tf_buffer_;
};

int main(int argc, char **argv)
{
  rclcpp::init(argc, argv);

  auto node = std::make_shared<HectorMapServer>();

  rclcpp::spin(node);

  rclcpp::shutdown();

  return 0;
}
