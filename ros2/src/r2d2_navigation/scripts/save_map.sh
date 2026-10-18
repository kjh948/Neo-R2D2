#!/usr/bin/env bash
# Save a running hector live map as a nav2 map_server .yaml/.pgm pair.
# Usage:
#   save_map.sh <path/without/ext>          # default topic /map (bringup session)
#   MAP_TOPIC=live_map save_map.sh ...      # navigation session (live map remapped)
set -euo pipefail
out="${1:-maps/r2d2_home}"
map_topic="${MAP_TOPIC:-map}"
mkdir -p "$(dirname "$out")"
ros2 run nav2_map_server map_saver_cli -f "$out" --ros-args -p map_topic:="${map_topic}"
