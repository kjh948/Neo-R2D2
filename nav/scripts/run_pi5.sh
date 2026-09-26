#!/usr/bin/env bash
# Pi5 stand-alone: llama-server + navserve + navigator all on the robot.
#   ./scripts/run_pi5.sh <instruction> [preset]
# For the recommended remote setup (inference on the Mac), run on the Pi:
#   ./venv/bin/python -m navstack drive --server ws://<mac>:8050 \
#       --robot localhost --camera r2d2 --instruction "..."
set -euo pipefail
cd "$(dirname "$0")/.."
INSTR="${1:?usage: run_pi5.sh 'go to the couch' [preset]}"
PRESET="${2:-lite}"

# Pi5 CPU: bf16 projector is fine here (aarch64 has native bf16 dot products)
export NAV_MMPROJ="${NAV_MMPROJ:-models/LightNav-0.mmproj-bf16.gguf}"
./venv/bin/python -m navstack supervise --task vln --preset "$PRESET" --log-level INFO &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT

# wait for navserve to bind (it warms up one synthetic inference first)
for i in $(seq 1 120); do
  if bash -c "exec 3<>/dev/tcp/127.0.0.1/8050" 2>/dev/null; then break; fi
  sleep 2
done

exec ./venv/bin/python -m navstack drive \
  --server ws://127.0.0.1:8050 --robot localhost \
  --camera r2d2 --instruction "$INSTR"
