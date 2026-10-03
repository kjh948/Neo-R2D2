#!/usr/bin/env bash
# Download the official GNM/ViNT/NoMaD checkpoints (Google Drive, ~1GB total).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=models/vint_weights
mkdir -p "$OUT"
if ls "$OUT"/checkpoints/*.pth >/dev/null 2>&1; then
  echo "have: $(ls "$OUT"/checkpoints/*.pth | tr '\n' ' ')"
  exit 0
fi
venv/bin/gdown --folder \
  "https://drive.google.com/drive/folders/1a9yWR2iooXFAqjQHetz263--4_2FFggg" \
  -O "$OUT/"
echo "weights in $OUT/checkpoints (vint.pth gnm.pth nomad.pth)"
