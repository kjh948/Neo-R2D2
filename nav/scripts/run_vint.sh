#!/usr/bin/env bash
# visualnav-transformer (ViNT) backend: in-process torch, ~8-10 Hz on CPU.
#   ./scripts/run_vint.sh serve [extra args]     # navserve on :8050
#   ./scripts/run_vint.sh drive "<goal.jpg>" [extra args]
set -euo pipefail
cd "$(dirname "$0")/.."
VINTCFG=visualnav-transformer/train/config/vint.yaml
VINTCKPT=models/vint_weights/checkpoints/vint.pth
PY=./venv/bin/python

case "${1:-}" in
  serve)
    shift
    exec $PY -m navstack serve --backend vint \
      --vint-config "$VINTCFG" --vint-ckpt "$VINTCKPT" "$@"
    ;;
  drive)
    shift
    GOAL="${1:?usage: run_vint.sh drive <goal.jpg> [--topomap DIR] [--robot IP] ...}"
    shift
    exec $PY -m navstack drive --server ws://127.0.0.1:8050 \
      --robot "${R2D2_HOST:-localhost}" --camera r2d2 \
      --goal-image "$GOAL" "$@"
    ;;
  *)
    echo "usage: $0 serve|drive ..."; exit 2;;
esac
