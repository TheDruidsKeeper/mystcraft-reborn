#!/usr/bin/env bash
# Build the mod jar inside Docker and copy it to ./out
# Usage: scripts/build.sh [gradle tasks...]   (default: build)
# Writes logs/build-docker.log.
set -uo pipefail
cd "$(dirname "$0")/.."
TASKS="${*:-build}"
mkdir -p logs
export DOCKER_BUILDKIT=1
docker buildx build \
  --progress=plain \
  --build-arg "GRADLE_TASKS=${TASKS}" \
  --target export \
  --output type=local,dest=out \
  . 2>&1 | tee logs/build-docker.log
build_status=${PIPESTATUS[0]}
if [ "$build_status" -ne 0 ]; then
  echo
  echo "BUILD FAILED - see logs/build-docker.log"
  exit "$build_status"
fi
echo "Built jars:"
ls -la out
