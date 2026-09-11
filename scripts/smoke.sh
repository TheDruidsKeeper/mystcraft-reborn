#!/usr/bin/env bash
# Headless smoke test: builds the mod, boots the NeoForge dedicated server with it installed and
# requires the server to finish loading. Writes out/smoke.log and smoke-docker.log.
# Usage: scripts/smoke.sh [seconds]
set -uo pipefail
cd "$(dirname "$0")/.."
SECONDS_BUDGET="${1:-420}"
export DOCKER_BUILDKIT=1
docker buildx build \
  --progress=plain \
  --build-arg "SMOKE_SECONDS=${SECONDS_BUDGET}" \
  --target smoke-export \
  --output type=local,dest=out \
  . 2>&1 | tee smoke-docker.log
build_status=${PIPESTATUS[0]}
if [ "$build_status" -ne 0 ]; then
  echo
  echo "SMOKE FAILED before the server ran - see smoke-docker.log"
  exit "$build_status"
fi

result="$(tr -d '[:space:]' < out/smoke-status.txt 2>/dev/null || echo MISSING)"
if [ "$result" = "PASSED" ]; then
  echo
  echo "SMOKE PASSED - server loaded and the self check passed. Full server log: out/smoke.log"
  ls -la out
  exit 0
fi
echo
echo "SMOKE FAILED ($result) - full server log: out/smoke.log, build output: smoke-docker.log"
ls -la out
exit 1
