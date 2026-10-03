#!/usr/bin/env bash
# Save the running hector live map (/live_map) as a nav2 map_server .yaml/.pgm
# pair. Usage:  save_map.sh <path/without/ext>   (default: maps/r2d2_home)
set -euo pipefail
out="${1:-maps/r2d2_home}"
mkdir -p "$(dirname "$out")"
ros2 run nav2_map_server map_saver_cli -f "$out" --ros-args -p map_topic:=live_map
