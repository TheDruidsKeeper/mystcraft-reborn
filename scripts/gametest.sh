#!/usr/bin/env bash
# Headless in-game tests: builds the mod, boots the NeoForge GameTest server with the dev-only test mod and runs every
# registered test. Writes out/logs/gametest.log, out/gametest-status.txt and logs/gametest-docker.log.
# Usage: scripts/gametest.sh
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs
export DOCKER_BUILDKIT=1
docker buildx build \
  --progress=plain \
  --target gametest-export \
  --output type=local,dest=out \
  . 2>&1 | tee logs/gametest-docker.log
build_status=${PIPESTATUS[0]}
if [ "$build_status" -ne 0 ]; then
  echo
  echo "GAMETESTS FAILED before the server ran - see logs/gametest-docker.log"
  exit "$build_status"
fi
result="$(tr -d '[:space:]' < out/gametest-status.txt 2>/dev/null || echo MISSING)"
if [ "$result" = "PASSED" ]; then
  echo
  echo "GAMETESTS PASSED - full log: out/logs/gametest.log"
  exit 0
fi
echo
echo "GAMETESTS FAILED ($result) - full log: out/logs/gametest.log, build output: logs/gametest-docker.log"
exit 1
