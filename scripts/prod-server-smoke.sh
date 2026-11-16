#!/usr/bin/env bash
# Installs a real NeoForge dedicated server (no dev environment), drops in the built JAR
# (with its Jar-in-Jar libraries) and checks that it starts and VESTIGE loads.
set -euo pipefail
cd "$(dirname "$0")/.."

NEO_VERSION=$(grep '^neo_version=' gradle.properties | cut -d= -f2)
MOD_VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
JAR="build/libs/vestige-${MOD_VERSION}.jar"
DIR="prod-server"
INSTALLER="$DIR/neoforge-${NEO_VERSION}-installer.jar"

[[ -f "$JAR" ]] || { echo "Missing $JAR; run ./gradlew build first" >&2; exit 1; }
mkdir -p "$DIR"
if [[ ! -f "$DIR/run.sh" ]]; then
  curl -fsSL -o "$INSTALLER" \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEO_VERSION}/neoforge-${NEO_VERSION}-installer.jar"
  (cd "$DIR" && java -jar "$(basename "$INSTALLER")" --install-server . > installer.log 2>&1)
fi

rm -rf "$DIR/mods" "$DIR/world" "$DIR/logs"
mkdir -p "$DIR/mods"
cp "$JAR" "$DIR/mods/"
echo "eula=true" > "$DIR/eula.txt"
printf 'online-mode=false\nlevel-name=world\n' > "$DIR/server.properties"
# The installer's user_jvm_args.txt ends without a newline; write the flag on its own line.
ARGS="$DIR/user_jvm_args.txt"
touch "$ARGS"
sed -i 's/-Dvestige\.dev\.autostop=true//' "$ARGS"
grep -qx -- '-Dvestige.dev.autostop=true' "$ARGS" || printf '\n-Dvestige.dev.autostop=true\n' >> "$ARGS"

(cd "$DIR" && timeout 600 ./run.sh nogui > console.log 2>&1) || true

LOG="$DIR/logs/latest.log"
grep -E "Loading [0-9]+ mods|- vestige |- geckolib |- playeranimator " "$LOG" || true
grep -q "VESTIGE SMOKE OK" "$LOG" || { echo "Production smoke FAILED; see $LOG" >&2; tail -40 "$LOG" >&2; exit 1; }
grep "VESTIGE SMOKE modules" "$LOG"
echo "Production-server smoke run passed"
