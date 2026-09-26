#!/usr/bin/env bash
# navstack runner (visualnav-transformer backend; NoMaD = goal-less default).
#
#   ./scripts/run_vint.sh explore [drive args...]        # NoMaD: wander+avoid
#   ./scripts/run_vint.sh goal <photo.jpg> [drive args]  # ViNT: navigate to photo
#   ./scripts/run_vint.sh map <topomap_dir> [drive args] # graph navigation
#
# Env: R2D2_HOST (robot Pi, default localhost), SERVER_HOST (navserve host,
# default 127.0.0.1:8050; this script starts a local server unless --no-serve).
set -euo pipefail
cd "$(dirname "$0")/.."
PY=./venv/bin/python
CFG_DIR=visualnav-transformer/train/config
CK_DIR=models/vint_weights/checkpoints
HOST="${R2D2_HOST:-localhost}"
MODE="${1:?usage: run_vint.sh explore|goal|map ...}"; shift

case "$MODE" in
  explore) SERVE_ARGS=(--vint-config "$CFG_DIR/nomad.yaml" --vint-ckpt "$CK_DIR/nomad.pth");;
  goal)    GOAL="${1:?need goal photo}"; shift
           SERVE_ARGS=(--vint-config "$CFG_DIR/vint.yaml" --vint-ckpt "$CK_DIR/vint.pth");;
  map)     MAP="${1:?need topomap dir}"; shift
           SERVE_ARGS=(--vint-config "$CFG_DIR/nomad.yaml" --vint-ckpt "$CK_DIR/nomad.pth" --topomap "$MAP");;
  *) echo "unknown mode $MODE"; exit 2;;
esac
DRIVE_GOAL=()
[ "${MODE:-}" = goal ] && DRIVE_GOAL=(--goal-image "$GOAL")
[ "${MODE:-}" = map ] && DRIVE_GOAL=(--topomap "$MAP")

if [[ "${1:-}" != "--no-serve" ]]; then
  $PY -m navstack serve "${SERVE_ARGS[@]}" &
  SERVER=$!
  trap 'kill $SERVER 2>/dev/null' EXIT
  for _ in $(seq 1 120); do
    lsof -nP -iTCP:8050 -sTCP:LISTEN >/dev/null 2>&1 && break; sleep 1; done
else
  shift
fi

exec $PY -m navstack drive --server ws://127.0.0.1:8050 --robot "$HOST" \
  --camera r2d2 "${DRIVE_GOAL[@]}" "$@"
