#!/usr/bin/env bash
# Build a self-contained Modrinth .mrpack and a MultiMC/Prism instance zip from a built jar.
# Usage: scripts/pack-instance.sh [jar]
# Default jar: newest out/mystcraft-neoforge-*.jar (excludes -sources / -javadoc).
# Writes out/mystcraft-reborn.mrpack and out/mystcraft-reborn-multimc.zip.
set -euo pipefail
cd "$(dirname "$0")/.."

prop() {
  local key="$1"
  local value
  value="$(sed -n "s/^${key}=//p" gradle.properties | head -n1 | tr -d '\r')"
  if [ -z "$value" ]; then
    echo "error: missing ${key} in gradle.properties" >&2
    exit 1
  fi
  echo "$value"
}

MOD_VERSION="$(prop mod_version)"
MOD_NAME="$(prop mod_name)"
NEO_VERSION="$(prop neo_version)"
# NeoForge 26.1.2.x runs on Minecraft 26.1.2 (Prism/MultiMC meta pin), not the short
# minecraft_version line used for jar naming.
MC_VERSION="$(printf '%s\n' "$NEO_VERSION" | cut -d. -f1-3)"

if [ "$#" -ge 1 ]; then
  JAR="$1"
else
  JAR="$(ls -1t out/mystcraft-neoforge-*.jar 2>/dev/null | grep -Ev -- '-(sources|javadoc)\.jar$' | head -n1 || true)"
fi
if [ -z "${JAR:-}" ] || [ ! -f "$JAR" ]; then
  echo "error: no jar found; pass a path or build first (out/mystcraft-neoforge-*.jar)" >&2
  exit 1
fi
JAR_BASENAME="$(basename "$JAR")"

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/mystcraft-pack.XXXXXX")"
cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

MRPACK_ROOT="$WORKDIR/mrpack"
MMC_ROOT="$WORKDIR/multimc"
mkdir -p "$MRPACK_ROOT/overrides/mods" "$MMC_ROOT/.minecraft/mods"
cp "$JAR" "$MRPACK_ROOT/overrides/mods/$JAR_BASENAME"
cp "$JAR" "$MMC_ROOT/.minecraft/mods/$JAR_BASENAME"

cat > "$MRPACK_ROOT/modrinth.index.json" <<EOF
{
  "formatVersion": 1,
  "game": "minecraft",
  "versionId": "${MOD_VERSION}",
  "name": "${MOD_NAME}",
  "summary": "Mystcraft Reborn playable instance: Minecraft ${MC_VERSION}, NeoForge ${NEO_VERSION}.",
  "files": [],
  "dependencies": {
    "minecraft": "${MC_VERSION}",
    "neoforge": "${NEO_VERSION}"
  }
}
EOF

cat > "$MMC_ROOT/mmc-pack.json" <<EOF
{
  "components": [
    {
      "important": true,
      "uid": "net.minecraft",
      "version": "${MC_VERSION}"
    },
    {
      "uid": "net.neoforged",
      "version": "${NEO_VERSION}"
    }
  ],
  "formatVersion": 1
}
EOF

cat > "$MMC_ROOT/instance.cfg" <<EOF
InstanceType=OneSix
name=${MOD_NAME}
OverrideJavaLocation=false
OverrideMemory=false
iconKey=default
EOF

# Prefer system zip; fall back to Python so the script works without zip installed.
zip_dir() {
  local dest="$1"
  local root="$2"
  shift 2
  if command -v zip >/dev/null 2>&1; then
    (
      cd "$root"
      zip -qr "$dest" "$@"
    )
  else
    python3 - "$dest" "$root" "$@" <<'PY'
import os, sys, zipfile
dest, root = sys.argv[1], sys.argv[2]
entries = sys.argv[3:]
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zf:
    for entry in entries:
        path = os.path.join(root, entry)
        if os.path.isdir(path):
            for dirpath, _, filenames in os.walk(path):
                for name in filenames:
                    full = os.path.join(dirpath, name)
                    arc = os.path.relpath(full, root).replace(os.sep, "/")
                    zf.write(full, arc)
        else:
            zf.write(path, entry.replace(os.sep, "/"))
PY
  fi
}

mkdir -p out
MRPACK_OUT="$(pwd)/out/mystcraft-reborn.mrpack"
MMC_OUT="$(pwd)/out/mystcraft-reborn-multimc.zip"
rm -f "$MRPACK_OUT" "$MMC_OUT"

zip_dir "$MRPACK_OUT" "$MRPACK_ROOT" modrinth.index.json overrides
zip_dir "$MMC_OUT" "$MMC_ROOT" instance.cfg mmc-pack.json .minecraft

echo "Packed instance:"
echo "  jar:          $JAR"
echo "  minecraft:    $MC_VERSION"
echo "  neoforge:     $NEO_VERSION"
echo "  mod_version:  $MOD_VERSION"
ls -la "$MRPACK_OUT" "$MMC_OUT"
