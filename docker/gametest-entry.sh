#!/bin/sh
# Runs INSIDE the Docker `gametest` stage (see Dockerfile). Boots the NeoForge GameTest server with the mod and
# the dev-only `mystcraft_tests` mod, runs every registered in-game test and exits.
#
# Success  = gradle exits 0 and the log reports the test summary with 0 failures.
# Output   = /out/logs/gametest.log (full log), /out/gametest-status.txt (PASSED / FAILED / DID_NOT_RUN),
#            /out/gametest-results/*.xml (JUnit summary from the test framework, when produced).
set -u

LOG=/out/logs/gametest.log
STATUS=/out/gametest-status.txt
mkdir -p /out/logs run/gametest
echo "DID_NOT_RUN" > "${STATUS}"

echo "----- running game tests -----"
./gradlew --no-daemon --stacktrace runGameTestServer > "${LOG}" 2>&1
code=$?
if [ -d run/gametest/gametest-results ]; then
  mkdir -p /out/gametest-results && cp -r run/gametest/gametest-results/. /out/gametest-results/ 2>/dev/null || true
fi

echo "----- mystcraft log lines -----"
grep -E "\[(spawn|link|worldgen|portal|instability)\]" "${LOG}" | sed 's/^\[[0-9:]*\] //' | head -80 || true

echo "----- test results -----"
grep -E "GameTest|gametest|TestFramework|tests? (passed|failed)|FAILED|PASSED|required tests" "${LOG}" \
    | grep -viE "Loading|DEBUG|testframework\.|AT /" | head -80 || true

if [ "${code}" -eq 0 ] && ! grep -qiE "([1-9][0-9]* )?required tests failed|GameTestException|FAILED TESTS" "${LOG}"; then
    echo "PASSED" > "${STATUS}"
    echo "----- GAMETESTS PASSED -----"
    exit 0
fi

echo "----- failing tests -----"
grep -B2 -A20 -E "GameTestException|failed|FAILED" "${LOG}" | grep -vE "^\s+at (org\.gradle|java\.base|jdk\.internal|worker\.org)" | head -160 || true
echo "----- last 60 lines -----"
grep -vE "^\s+at (org\.gradle|java\.base|jdk\.internal|worker\.org)" "${LOG}" | tail -60
echo "FAILED" > "${STATUS}"
echo "----- GAMETESTS FAILED (gradle exit ${code}) -----"
exit 0
