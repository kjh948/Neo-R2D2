#!/usr/bin/env bash
# Clone the upstream LightNav-0 release into nav/LightNav-0.
# navstack imports the torch-free modules straight from LightNav-0/src (see
# navstack/__init__.py); the full repo also ships mujoco_demo & robot_deploy.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -d LightNav-0 ] && { echo "have LightNav-0"; exit 0; }
git clone --depth 1 https://github.com/lightorigins/LightNav-0.git LightNav-0
