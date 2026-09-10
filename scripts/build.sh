#!/usr/bin/env bash
# Build the mod jar inside Docker and copy it to ./out
# Usage: scripts/build.sh [gradle tasks...]   (default: build)
set -euo pipefail
cd "$(dirname "$0")/.."
TASKS="${*:-build}"
export DOCKER_BUILDKIT=1
docker buildx build \
  --progress=plain \
  --build-arg "GRADLE_TASKS=${TASKS}" \
  --target export \
  --output type=local,dest=out \
  .
echo "Built jars:"
ls -la out
