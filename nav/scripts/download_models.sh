#!/usr/bin/env bash
# Download the LightNav-0 GGUF (4-bit) + vision projector + checkpoint metadata.
# LLM quants: https://huggingface.co/prithivMLmods/LightNav-0-GGUF
# Metadata / RVQ bundle: https://huggingface.co/LightOriginsHQ/LightNav-0 (Apache-2.0)
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p models/action_tokenizer

dl() { # dl <out> <url>
  [ -s "models/$1" ] && { echo "have $1"; return; }
  echo "downloading $1"
  curl -fL --retry 3 -o "models/$1" "$2"
}

G="https://huggingface.co/prithivMLmods/LightNav-0-GGUF/resolve/main"
H="https://huggingface.co/LightOriginsHQ/LightNav-0/resolve/main"

dl LightNav-0.Q4_K_M.gguf        "$G/LightNav-0.Q4_K_M.gguf"
dl LightNav-0.mmproj-bf16.gguf   "$G/LightNav-0.mmproj-bf16.gguf"
for f in manifest.json codebook_l0.npy codebook_l1.npy codebook_l2.npy \
         jacobian_weights.npy alpha_per_source.json \
         distance_l0.npy distance_l1.npy distance_l2.npy; do
  dl "action_tokenizer/$f" "$H/action_tokenizer/$f"
done
dl eval_config.json   "$H/eval_config.json"
dl config.json        "$H/config.json"
dl tokenizer_config.json "$H/tokenizer_config.json"

# The bf16 mmproj runs ~10x slow on x86 CPUs (no native bf16): convert to f16.
if [ ! -s models/LightNav-0.mmproj-f16.gguf ] && [ -s models/LightNav-0.mmproj-bf16.gguf ]; then
  echo "converting mmproj bf16 -> f16"
  ./venv/bin/python scripts/convert_mmproj_f16.py \
    models/LightNav-0.mmproj-bf16.gguf models/LightNav-0.mmproj-f16.gguf
fi
echo "done: $(du -sh models | cut -f1)"
