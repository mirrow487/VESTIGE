#!/usr/bin/env bash
# Full local verification: build + unit tests, GameTests, dev-server smoke run, production-server smoke run.
# Usage: scripts/verify.sh [--skip-prod]
set -euo pipefail
cd "$(dirname "$0")/.."

step() { printf '\n==> %s\n' "$*"; }

step "Build, unit tests and architecture rules"
./gradlew build

step "GameTests (headless)"
./gradlew runGameTestServer
grep -E "All [0-9]+ required tests passed" runs/gametest/logs/latest.log

step "Dev dedicated-server smoke run"
mkdir -p runs/server
echo "eula=true" > runs/server/eula.txt
./gradlew runServer -Psmoke
grep -q "VESTIGE SMOKE OK" runs/server/logs/latest.log
grep "VESTIGE SMOKE modules" runs/server/logs/latest.log

if [[ "${1:-}" != "--skip-prod" ]]; then
  step "Production-server smoke run with the built JAR"
  scripts/prod-server-smoke.sh
fi

step "All checks passed"
