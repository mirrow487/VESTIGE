#!/usr/bin/env bash
# Multiplayer test: a dev dedicated server (offline mode, flat world) and two scripted clients.
#   Client A (Dev, driver mp_a): equips through the real menu, enters stance, cycles relics, sends a forged packet.
#   Client B (Dev2, driver mp_b): checks what it receives about A and screenshots A's armor and weapons.
# Usage: scripts/multiplayer-test.sh      Results: runs/client{,2}/logs/latest.log, screenshots in runs/client{,2}/screenshots/
set -euo pipefail
cd "$(dirname "$0")/.."

SERVER=runs/mpserver
rm -rf "$SERVER" runs/client/screenshots runs/client2/screenshots runs/client/logs/latest.log runs/client2/logs/latest.log
mkdir -p "$SERVER" runs/client runs/client2
echo "eula=true" > "$SERVER/eula.txt"
cat > "$SERVER/server.properties" <<'EOF'
online-mode=false
difficulty=peaceful
spawn-protection=0
level-type=minecraft\:flat
generate-structures=false
spawn-monsters=false
view-distance=6
simulation-distance=6
EOF
# Both test players are operators (offline-mode UUIDs of "Dev" and "Dev2").
cat > "$SERVER/ops.json" <<'EOF'
[{"uuid":"380df991-f603-344c-a090-369bad2a924a","name":"Dev","level":4,"bypassesPlayerLimit":false},
 {"uuid":"21cc3e25-481e-3cfa-8770-c5693a3e93a4","name":"Dev2","level":4,"bypassesPlayerLimit":false}]
EOF
OPTS='onboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:30\n'
printf "$OPTS" > runs/client/options.txt
printf "$OPTS" > runs/client2/options.txt

cleanup() {
  pkill -f clientRunProgramArgs || true
  pkill -f client2RunProgramArgs || true
  pkill -f serverRunProgramArgs || true
}
trap cleanup EXIT

./gradlew runServer -PserverDir="$SERVER" > "$SERVER/console.txt" 2>&1 &
for _ in $(seq 1 120); do
  grep -q "Done (" "$SERVER/logs/latest.log" 2>/dev/null && break
  sleep 2
done
grep -q "Done (" "$SERVER/logs/latest.log" || { echo "Server did not start" >&2; exit 1; }

LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -PquickPlayServer=localhost -Pdriver=mp_a \
  > runs/client/console.txt 2>&1 &
A=$!
# B joins once A has equipped and entered stance.
for _ in $(seq 1 150); do
  grep -q "DRIVER MP A READY" runs/client/logs/latest.log 2>/dev/null && break
  sleep 2
done
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient2 -PquickPlayServer=localhost -Pdriver2=mp_b \
  > runs/client2/console.txt 2>&1 &
B=$!
wait "$B" || true
wait "$A" || true

status=0
for log in runs/client/logs/latest.log runs/client2/logs/latest.log; do
  echo "== $log"
  grep -E "DRIVER (CHECK|DONE)" "$log" | sed 's/^.*\] //' | sort -u || true
  grep -qE "DRIVER DONE .*failures=0 " "$log" || status=1
done
grep -E "VESTIGE|Exception" "$SERVER/logs/latest.log" | grep -v "^\s*at " | tail -5 || true
exit $status
