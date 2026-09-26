#!/usr/bin/env bash
# Mac host: llama-server + navserve (the compute-heavy inference side).
#   ./scripts/run_mac.sh [preset]     preset: full|lite|micro|nano (default lite)
set -euo pipefail
cd "$(dirname "$0")/.."
PRESET="${1:-lite}"
# prefer the native source build (brew bottle is 4-8x slower on x86 CPUs)
if [ -z "${LLAMA_BIN:-}" ]; then
  if [ -x llama.cpp-src/build/bin/llama-server ]; then
    LLAMA_BIN=$PWD/llama.cpp-src/build/bin/llama-server
  else
    LLAMA_BIN=llama-server
  fi
fi
export LLAMA_BIN

# prefer the f16 projector (x86 CPUs are pathologically slow on bf16)
if [ -s models/LightNav-0.mmproj-f16.gguf ]; then
  export NAV_MMPROJ=models/LightNav-0.mmproj-f16.gguf
else
  export NAV_MMPROJ=models/LightNav-0.mmproj-bf16.gguf
fi

exec ./venv/bin/python -m navstack supervise --task vln --preset "$PRESET" \
  --log-level INFO
