#!/usr/bin/env bash
# Runs a scripted client scenario headlessly (Xvfb + software OpenGL) in a fresh copy of a world.
# Usage: scripts/client-driver.sh [scenario]   (combat | anim | calib; default combat)
# Screenshots: runs/client/screenshots/   Checks: grep "DRIVER" runs/client/logs/latest.log
set -euo pipefail
cd "$(dirname "$0")/.."
SCENARIO="${1:-combat}"

if [[ ! -d runs/server/world ]]; then
  mkdir -p runs/server && echo "eula=true" > runs/server/eula.txt
  ./gradlew runServer -Psmoke
fi
# Remove the previous log too, so a failed build can never be mistaken for a test result.
rm -rf runs/client/saves/driver runs/client/screenshots runs/client/logs/latest.log
mkdir -p runs/client/saves
cp -r runs/server/world runs/client/saves/driver
printf 'onboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:60\n' > runs/client/options.txt

LOG=runs/client/logs/latest.log
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -PquickPlay=driver -Pdriver="$SCENARIO" &
CLIENT_PID=$!
# Watchdog: a mod-loading crash leaves the client on an error screen forever; stop it at once.
while kill -0 "$CLIENT_PID" 2>/dev/null; do
  if [[ -f "$LOG" ]] && grep -qE "Failed to create mod instance|Preparing crash report" "$LOG"; then
    echo "Client crashed during startup; see $LOG" >&2
    pkill -f clientRunProgramArgs || true
    wait "$CLIENT_PID" || true
    exit 1
  fi
  sleep 3
done
wait "$CLIENT_PID" || { echo "Client run failed (build error?)" >&2; exit 1; }

grep -E "DRIVER (CHECK|DONE)" "$LOG" | sed 's/^.*\] //' | sort -u
grep -q "DRIVER DONE" "$LOG" || { echo "Driver did not finish" >&2; exit 1; }
grep -qE "DRIVER DONE .*failures=0 " "$LOG" || { echo "Driver reported failures" >&2; exit 1; }
