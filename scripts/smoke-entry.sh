#!/bin/sh
# Runs INSIDE the Docker `smoke` stage (see Dockerfile). Boots the NeoForge dedicated server with
# the mod installed and requires it to finish loading.
#
# Success  = the server log contains "Done (" (server finished loading).
# Failure  = anything else; the extracted errors are printed to the build output.
#
# Kept as a separate file (not a Dockerfile heredoc) so that a CRLF line ending in the Dockerfile
# cannot break the shell. .gitattributes pins this file to LF.
set -u

SMOKE_SECONDS="${SMOKE_SECONDS:-420}"
LOG=/out/smoke.log
mkdir -p /out run/server

echo "eula=true" > run/server/eula.txt
{
  echo "online-mode=false"
  echo "max-tick-time=-1"
  echo "level-name=smoke"
  echo "sync-chunk-writes=false"
} > run/server/server.properties

echo "----- booting dedicated server (budget ${SMOKE_SECONDS}s) -----"
# MYSTCRAFT_SELFCHECK makes the mod run its end-to-end verification on ServerStarted (see SelfCheck.java):
# registries bound, symbols/grammar sane, dimension type parsed, an Age created and a chunk generated.
# The server never exits on its own; stop it once it has loaded (or when the budget runs out).
MYSTCRAFT_SELFCHECK=1 timeout --preserve-status -s TERM "${SMOKE_SECONDS}" \
    ./gradlew --no-daemon --stacktrace runServer > "${LOG}" 2>&1 || true

echo "----- mystcraft log lines -----"
grep -i "mystcraft" "${LOG}" | head -60 || true

echo "----- self check -----"
grep -E "\[selfcheck\]|SELFCHECK" "${LOG}" | sed 's/^\[[0-9:]*\] //' || true

if grep -q 'Done (' "${LOG}"; then
    if grep -q 'SELFCHECK FAILED' "${LOG}"; then
        echo "----- SMOKE FAILED: server loaded but the self check failed -----"
        exit 1
    fi
    if ! grep -q 'SELFCHECK PASSED' "${LOG}"; then
        echo "----- SMOKE FAILED: server loaded but the self check did not run -----"
        exit 1
    fi
    echo "----- SMOKE PASSED: server loaded and the self check passed -----"
    echo "warnings/errors mentioning mystcraft:"
    grep -icE "(WARN|ERROR).*mystcraft" "${LOG}" || true
    grep -iE "(WARN|ERROR).*mystcraft" "${LOG}" | head -40 || true
    exit 0
fi

echo "----- SMOKE FAILED: server never finished loading -----"

# Mod-loading rejections (dependency ranges, missing mods) come with the detail on following lines.
echo "----- mod loading errors -----"
grep -A25 -E "Loading errors encountered|Missing or unsupported mandatory dependencies" "${LOG}" | head -60 || true

echo "----- registry / datapack / codec errors -----"
grep -nE "ERROR|Exception|Caused by|Failed to|Missing|Unknown registry|No key|Suppressed|requires|Expected range" "${LOG}" \
    | grep -vE "CancellationException|ZipFile closed|background-scan" | head -60 || true

# The tail of the raw log is mostly Gradle stack frames; drop those so the game output is visible.
echo "----- last 80 lines of game output -----"
grep -vE "^\s+at (org\.gradle|java\.base|jdk\.internal|worker\.org)" "${LOG}" | tail -80
exit 1
