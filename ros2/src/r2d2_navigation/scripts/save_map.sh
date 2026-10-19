#!/usr/bin/env bash
# Save the running slam_toolbox map from a bringup (async mapping) session:
#   1) /slam_toolbox/serialize_map  -> <out>_posegraph.posegraph + .data
#      (what navigation.launch.py localizes against: posegraph:=<out>_posegraph)
#   2) map_saver_cli on /map        -> <out>.pgm + <out>.yaml (viewing/archive)
#
# Usage:
#   save_map.sh                          # -> <ws>/src/r2d2_navigation/maps/r2d2_home
#   save_map.sh <path/without/ext>       # custom output location
#   SKIP_POSEGRAPH=1 / SKIP_PGM=1        # skip either step
#
# The default location is the package's git-tracked src maps/ dir, resolved
# from this script's REAL path (<ws>/install/r2d2_navigation/lib/r2d2_navigation/
# save_map.sh), so `ros2 run` behaves identically from any CWD. After saving:
#   colcon build --packages-select r2d2_navigation
# to refresh the install copy that navigation.launch.py posegraph:= refers to.
set -euo pipefail

script_dir="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
case "$script_dir" in
  */src/r2d2_navigation/scripts)  # running straight from the source tree
    maps_dir="$(dirname "$script_dir")/maps" ;;
  *)                              # colcon-installed layout
    maps_dir="$script_dir/../../../../src/r2d2_navigation/maps" ;;
esac
if ! maps_dir="$(cd "$maps_dir" 2>/dev/null && pwd)"; then
  echo "save_map.sh: no workspace src tree relative to $script_dir — saving under \$PWD/maps instead" >&2
  maps_dir="$PWD/maps"
fi

out="${1:-$maps_dir/r2d2_home}"
# absolute path: slam_toolbox resolves a relative map_file_name against ITS
# own cwd (the bringup terminal), never ours
mkdir -p "$(dirname "$out")"
out="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"

if [[ "${SKIP_POSEGRAPH:-0}" != 1 ]]; then
  echo "[save_map.sh] serializing posegraph -> ${out}_posegraph.posegraph/.data"
  ros2 service call /slam_toolbox/serialize_map slam_toolbox/srv/SerializePoseGraph \
    "{filename: '${out}_posegraph'}"
fi

if [[ "${SKIP_PGM:-0}" != 1 ]]; then
  echo "[save_map.sh] saving pgm/yaml from /map"
  map_topic="${MAP_TOPIC:-map}"
  ros2 run nav2_map_server map_saver_cli -f "$out" --ros-args -p map_topic:="${map_topic}"
fi

echo "saved -> ${out}_posegraph.posegraph/.data + ${out}.pgm/.yaml (see SKIP_* knobs)"
