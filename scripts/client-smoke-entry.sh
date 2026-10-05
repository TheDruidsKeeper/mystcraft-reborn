#!/bin/sh
# Runs INSIDE the Docker `client-smoke` stage (see Dockerfile). Starts the NeoForge dev client under Xvfb with
# Mesa software OpenGL and MYSTCRAFT_CLIENT_SELFCHECK=1, which makes ClientSelfCheck drive the game (fresh flat
# world -> /myst-dev scene -> /myst visit into a new Age -> night) taking screenshots, then quit.
#
# Success  = "CLIENT SELFCHECK PASSED" in the log and no mod-related render/resource warnings.
# Output   = /out/client-smoke.log, /out/client-smoke-status.txt, /out/screenshots/selfcheck_*.png, /out/qa-report.txt
set -u

BUDGET="${CLIENT_SMOKE_SECONDS:-1200}"
LOG=/out/client-smoke.log
STATUS=/out/client-smoke-status.txt
mkdir -p /out run/client
echo "DID_NOT_RUN" > "${STATUS}"

# Skip the dev-client "do you want to log in" style prompts and any first-run UI.
mkdir -p run/client
cat > run/client/options.txt <<'OPTS'
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
narrator:0
fullscreen:false
overrideWidth:1280
overrideHeight:720
renderDistance:8
soundCategory_master:0.0
OPTS

export MYSTCRAFT_CLIENT_SELFCHECK=1
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=4.5
export ALSOFT_DRIVERS=null

echo "----- booting client under Xvfb (budget ${BUDGET}s) -----"
timeout --preserve-status -s TERM "${BUDGET}" \
    xvfb-run --auto-servernum --server-args="-screen 0 1280x720x24 +extension GLX +render -noreset" \
    ./gradlew --no-daemon --stacktrace runClient > "${LOG}" 2>&1 || true

if [ -d run/client/screenshots ]; then
    mkdir -p /out/screenshots
    cp run/client/screenshots/selfcheck_*.png /out/screenshots/ 2>/dev/null || true
fi

echo "----- client check lines -----"
grep -E "\[clientcheck\]|CLIENT SELFCHECK" "${LOG}" | sed 's/^\[[0-9:]*\] //' | head -80 || true

echo "----- resource / render warnings mentioning the mod -----"
grep -E "Missing textures|Missing FluidModel|Unable to load model|Failed to load|Exception loading|Missing sound|Unknown render|mystcraft:.*(missing|Missing)" "${LOG}" \
    | grep -i "mystcraft" | head -40 || true
WARNINGS=$(grep -E "Missing textures|Missing FluidModel|Unable to load model|Missing sound" "${LOG}" | grep -ic "mystcraft" || true)
echo "mod resource warnings: ${WARNINGS}"

echo "----- mystcraft errors -----"
grep -E "ERROR.*(mystcraft|Mystcraft)|com\.tbd" "${LOG}" | grep -vE "^\s+at " | head -40 || true
ERRORS=$(grep -E "\]/ERROR\]|/ERROR\] \[com\.tbd|ERROR\] \[com\.tbd" "${LOG}" | grep -c "tbd" || true)

echo "----- QA shelf visual regression (scripts/qa/compare.py) -----"
VISUAL=0
if ls /out/screenshots/selfcheck_qa_*.png >/dev/null 2>&1; then
    python3 /usr/local/lib/mystcraft-qa/compare.py /out/screenshots --report /out/qa-report.txt || VISUAL=$?
else
    echo "no QA tour screenshots"
    VISUAL=1
fi

if grep -q "CLIENT SELFCHECK PASSED" "${LOG}" && [ "${WARNINGS}" = "0" ] && [ "${VISUAL}" = "0" ]; then
    echo "PASSED" > "${STATUS}"
    echo "----- CLIENT SMOKE PASSED (mod errors logged: ${ERRORS}) -----"
    ls -la /out/screenshots 2>/dev/null || true
    exit 0
fi

echo "----- crash reports -----"
ls run/client/crash-reports 2>/dev/null && cat run/client/crash-reports/*.txt 2>/dev/null | head -120 || true
echo "----- last 80 lines of game output -----"
grep -vE "^\s+at (org\.gradle|java\.base|jdk\.internal|worker\.org)" "${LOG}" | tail -80
if grep -q "CLIENT SELFCHECK PASSED" "${LOG}" && [ "${WARNINGS}" = "0" ]; then
    echo "VISUAL_DRIFT" > "${STATUS}"
    echo "----- CLIENT SMOKE FAILED: QA screenshots drifted from scripts/qa/baselines.json (see /out/qa-report.txt; review, then compare.py --update) -----"
elif grep -q "CLIENT SELFCHECK PASSED" "${LOG}"; then
    echo "RESOURCE_WARNINGS" > "${STATUS}"
    echo "----- CLIENT SMOKE FAILED: script passed but ${WARNINGS} mod resource warnings -----"
elif grep -q "CLIENT SELFCHECK FAILED" "${LOG}"; then
    echo "SELFCHECK_FAILED" > "${STATUS}"
    echo "----- CLIENT SMOKE FAILED: self check failed -----"
else
    echo "CLIENT_DID_NOT_FINISH" > "${STATUS}"
    echo "----- CLIENT SMOKE FAILED: client never completed the script -----"
fi
exit 0
