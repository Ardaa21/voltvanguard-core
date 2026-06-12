#!/usr/bin/env bash
# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  VoltVanguard — Flutter iOS Simulator Launcher                         ║
# ║  Run from: mobile/voltvanguard_app/                                    ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# Usage:
#   chmod +x run_simulator.sh && ./run_simulator.sh
#
# What it does:
#   1. Checks Flutter SDK is installed
#   2. Finds an available iPhone simulator (prefers iPhone 15 Pro)
#   3. Boots the simulator if it's shut down
#   4. flutter pub get
#   5. flutter run on that simulator with verbose Dio/WS logs enabled
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail

RESET='\033[0m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'
YELLOW='\033[0;33m'; RED='\033[0;31m'; DIM='\033[2m'; BOLD='\033[1m'

log_ok()   { echo -e "${GREEN}  🟢  $1${RESET}"; }
log_step() { echo -e "${CYAN}${BOLD}  ▶  $1${RESET}"; }
log_warn() { echo -e "${YELLOW}  🟡  $1${RESET}"; }
log_err()  { echo -e "${RED}  🔴  $1${RESET}"; }

echo ""
echo -e "${BOLD}${CYAN}  ⚡  VoltVanguard Flutter Simulator Launcher${RESET}"
echo ""

# ── 1. Flutter check ──────────────────────────────────────────────────────
if ! command -v flutter &>/dev/null; then
  log_err "Flutter not found. Install from https://flutter.dev/docs/get-started/install"
  exit 1
fi

FLUTTER_VER=$(flutter --version --machine 2>/dev/null | python3 -c \
  "import sys,json; d=json.load(sys.stdin); print(d.get('frameworkVersion','unknown'))" \
  2>/dev/null || echo "unknown")
log_ok "Flutter SDK found (v${FLUTTER_VER})"

# ── 2. Find simulator ─────────────────────────────────────────────────────
log_step "Searching for available iPhone simulator…"

# xcrun simctl list devices --json gives us all simulators
SIM_JSON=$(xcrun simctl list devices available --json 2>/dev/null)

# Prefer iPhone 15 Pro, fall back to iPhone 15, then any available iPhone
DEVICE_UDID=""
DEVICE_NAME=""

for preferred in "iPhone 16 Pro" "iPhone 15 Pro" "iPhone 15" "iPhone 14 Pro" "iPhone 14" "iPhone 13"; do
  result=$(echo "$SIM_JSON" | python3 -c "
import sys, json
data = json.load(sys.stdin)
target = '$preferred'
for runtime, devices in data.get('devices', {}).items():
    if 'iOS' not in runtime:
        continue
    for d in devices:
        if d.get('isAvailable') and target in d.get('name',''):
            print(d['udid'] + '|' + d['name'])
            sys.exit(0)
" 2>/dev/null || true)
  if [[ -n "$result" ]]; then
    DEVICE_UDID="${result%%|*}"
    DEVICE_NAME="${result##*|}"
    break
  fi
done

# Last resort: any available iPhone
if [[ -z "$DEVICE_UDID" ]]; then
  result=$(echo "$SIM_JSON" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for runtime, devices in data.get('devices', {}).items():
    if 'iOS' not in runtime:
        continue
    for d in devices:
        if d.get('isAvailable') and 'iPhone' in d.get('name',''):
            print(d['udid'] + '|' + d['name'])
            sys.exit(0)
" 2>/dev/null || true)
  if [[ -n "$result" ]]; then
    DEVICE_UDID="${result%%|*}"
    DEVICE_NAME="${result##*|}"
  fi
fi

if [[ -z "$DEVICE_UDID" ]]; then
  log_err "No available iPhone simulator found."
  log_err "Open Xcode → Window → Devices and Simulators → add a simulator."
  exit 1
fi

log_ok "Selected simulator: ${DEVICE_NAME} (${DEVICE_UDID})"

# ── 3. Boot simulator if needed ───────────────────────────────────────────
SIM_STATE=$(xcrun simctl list devices | grep "$DEVICE_UDID" | grep -o "(.*)" | tr -d '()' || echo "unknown")

if [[ "$SIM_STATE" != "Booted" ]]; then
  log_step "Booting simulator…"
  xcrun simctl boot "$DEVICE_UDID" 2>/dev/null || true
  open -a Simulator
  # Wait for it to be ready
  ELAPSED=0
  until xcrun simctl list devices | grep "$DEVICE_UDID" | grep -q "Booted"; do
    sleep 2; ELAPSED=$((ELAPSED + 2))
    if [[ $ELAPSED -ge 60 ]]; then
      log_warn "Simulator boot timed out — continuing anyway"
      break
    fi
  done
  log_ok "Simulator booted"
else
  log_ok "Simulator already running"
fi

# ── 4. flutter pub get ────────────────────────────────────────────────────
log_step "Installing Flutter dependencies (pub get)…"
flutter pub get 2>&1 | tail -5 | while IFS= read -r l; do echo -e "${DIM}     $l${RESET}"; done
log_ok "Dependencies resolved"

# ── 5. Health-check: is the backend up? ───────────────────────────────────
log_step "Checking Spring Boot backend reachability…"
if curl -sf "http://localhost:8080/api/v1/actuator/health" -o /dev/null 2>/dev/null; then
  log_ok "Backend is reachable at http://localhost:8080/api/v1"
else
  log_warn "Backend not reachable — start it first with:"
  log_warn "  cd ../../  &&  ./start_all.sh"
  log_warn "Continuing anyway (app will show connection errors until backend is up)"
fi

# ── 6. Run on simulator ───────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}  Launching VoltVanguard on ${DEVICE_NAME}…${RESET}"
echo -e "${DIM}  Hot reload: press r   |   Hot restart: press R   |   Quit: press q${RESET}"
echo ""

flutter run \
  --device-id "$DEVICE_UDID" \
  --debug \
  --dart-define=FLUTTER_ENV=dev
