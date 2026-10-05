#!/usr/bin/env bash
# Headless CLIENT smoke test: boots the dev client under Xvfb + Mesa inside Docker, lets ClientSelfCheck play through
# world creation, the debug scene and a link into a new Age, and exports screenshots.
# Writes out/client-smoke.log, out/client-smoke-status.txt, out/screenshots/, client-smoke-docker.log.
# Usage: scripts/client-smoke.sh [seconds]
set -uo pipefail
cd "$(dirname "$0")/.."
SECONDS_BUDGET="${1:-1200}"
export DOCKER_BUILDKIT=1
docker buildx build \
  --progress=plain \
  --build-arg "CLIENT_SMOKE_SECONDS=${SECONDS_BUDGET}" \
  --target client-smoke-export \
  --output type=local,dest=out \
  . 2>&1 | tee client-smoke-docker.log
build_status=${PIPESTATUS[0]}
if [ "$build_status" -ne 0 ]; then
  echo
  echo "CLIENT SMOKE FAILED before the client ran - see client-smoke-docker.log"
  exit "$build_status"
fi
result="$(tr -d '[:space:]' < out/client-smoke-status.txt 2>/dev/null || echo MISSING)"
if [ "$result" = "PASSED" ]; then
  echo
  echo "CLIENT SMOKE PASSED - log: out/client-smoke.log, screenshots: out/screenshots/"
  exit 0
fi
echo
echo "CLIENT SMOKE FAILED ($result) - log: out/client-smoke.log, build output: client-smoke-docker.log"
exit 1
