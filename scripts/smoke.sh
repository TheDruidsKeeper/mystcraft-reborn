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
status=${PIPESTATUS[0]}
if [ "$status" -eq 0 ]; then
  echo
  echo "SMOKE PASSED - server loaded the mod successfully. Full server log: out/smoke.log"
else
  echo
  echo "SMOKE FAILED (exit $status) - see smoke-docker.log for the extracted errors."
fi
ls -la out
exit "$status"
