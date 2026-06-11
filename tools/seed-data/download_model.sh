#!/usr/bin/env bash
# Download MiniLM ONNX assets for offline Android demo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/androidApp/src/androidMain/assets/models"
BASE="https://huggingface.co/Qdrant/all-MiniLM-L6-v2-onnx/resolve/main"

mkdir -p "$OUT"
for file in model.onnx tokenizer.json config.json special_tokens_map.json tokenizer_config.json; do
  echo "Downloading $file..."
  curl -fsSL "$BASE/$file" -o "$OUT/$file"
done
echo "Model files written to $OUT"
