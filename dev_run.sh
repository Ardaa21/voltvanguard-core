#!/usr/bin/env bash
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  VoltVanguard — One-Shot Full-Stack Dev Launcher                           ║
# ║                                                                            ║
# ║  Usage:  ./dev_run.sh [flutter run flags]                                  ║
# ║  Example: ./dev_run.sh -d "iPhone 15"                                      ║
# ║                                                                            ║
# ║  What it does:                                                             ║
# ║    1. Starts all backend services via start_all.sh                         ║
# ║       (Docker infra → Spring Boot → Python AI Agent)                      ║
# ║    2. Waits for backend health gate (Spring Boot /actuator/health)         ║
# ║    3. Launches `flutter run` in the mobile app directory                  ║
# ║    4. When you press q in Flutter → automatically runs stop_all.sh         ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
set -uo pipefail   # -e intentionally removed: flutter run exit-code must not kill cleanup

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLUTTER_DIR="$SCRIPT_DIR/mobile/voltvanguard_app"
# Spring Boot context-path is /api/v1 → actuator lives there too.
HEALTH_URL="http://localhost:8080/api/v1/actuator/health"
MAX_WAIT=20  # start_all.sh already confirmed healthy; this is a quick safety re-check

# ── Validate arguments — strip accidental shell-comment tokens ────────────────
# Users sometimes copy examples verbatim including "# comment" text. Filter it.
FLUTTER_ARGS=()
for arg in "$@"; do
  [[ "$arg" == \#* ]] && break   # stop at first # — rest is comment noise
  FLUTTER_ARGS+=("$arg")
done

# ── Colours ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[0;33m'; CYAN='\033[0;36m'
RED='\033[0;31m'; BOLD='\033[1m'; DIM='\033[2m'; RESET='\033[0m'

banner() {
  echo ""
  echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════╗${RESET}"
  echo -e "${BOLD}${CYAN}║  ⚡  VoltVanguard Full-Stack Dev Runner  ║${RESET}"
  echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════╝${RESET}"
  echo ""
}

log_ok()   { echo -e "${GREEN}  ✓  $*${RESET}"; }
log_info() { echo -e "${CYAN}  →  $*${RESET}"; }
log_warn() { echo -e "${YELLOW}  ⚠  $*${RESET}"; }
log_err()  { echo -e "${RED}  ✗  $*${RESET}"; }

# ── Cleanup on exit ───────────────────────────────────────────────────────────
cleanup() {
  echo ""
  log_info "Flutter exited — shutting down backend services…"
  "$SCRIPT_DIR/stop_all.sh" 2>/dev/null || true
  echo ""
  echo -e "${CYAN}${BOLD}  VoltVanguard dev session ended. ⚡${RESET}"
  echo ""
}
trap cleanup EXIT

# ── 1. Start backend ──────────────────────────────────────────────────────────
banner
log_info "Starting backend services (Docker + Spring Boot + AI Agent)…"
echo ""
"$SCRIPT_DIR/start_all.sh"
echo ""

# ── 2. Backend health gate ─────────────────────────────────────────────────────
# start_all.sh already blocks until Spring Boot is healthy before it exits.
# This is a short safety re-check in case of transient issues.
log_info "Verifying backend health at $HEALTH_URL"
ELAPSED=0
while true; do
  # Use -sf: silent + fail-on-4xx/5xx. grep "UP" guards against 503 partials.
  BODY=$(curl -sf "$HEALTH_URL" 2>/dev/null || true)
  if echo "$BODY" | grep -q '"UP"'; then
    log_ok "Backend is healthy! ✓"
    break
  fi

  if [[ $ELAPSED -ge $MAX_WAIT ]]; then
    log_warn "Health re-check timed out — proceeding (start_all.sh confirmed healthy)."
    break
  fi

  printf "${DIM}  [%2ds] Re-checking…${RESET}\r" "$ELAPSED"
  sleep 2
  ELAPSED=$((ELAPSED + 2))
done
echo ""

# ── 3. Device selection ───────────────────────────────────────────────────────

if [[ ! -d "$FLUTTER_DIR" ]]; then
  log_err "Flutter project not found at: $FLUTTER_DIR"
  exit 1
fi

cd "$FLUTTER_DIR"

# If no -d / --device-id flag was given, auto-pick a simulator so flutter run
# doesn't hang waiting for interactive input in a terminal session.
HAS_DEVICE_FLAG=false
for a in "${FLUTTER_ARGS[@]+"${FLUTTER_ARGS[@]}"}"; do
  [[ "$a" == -d || "$a" == --device-id ]] && HAS_DEVICE_FLAG=true && break
done

if ! $HAS_DEVICE_FLAG; then
  log_info "Detecting available Flutter devices…"
  # Prefer a booted iOS Simulator; fall back to whatever flutter can find.
  SIM_ID=$(xcrun simctl list devices booted 2>/dev/null \
            | grep -m1 'iPhone\|iPad' \
            | grep -oE '[0-9A-F-]{36}' || true)
  if [[ -n "$SIM_ID" ]]; then
    log_ok "Auto-selected booted simulator: $SIM_ID"
    FLUTTER_ARGS+=(-d "$SIM_ID")
  else
    log_warn "No booted simulator found — Flutter will ask you to pick a device."
    log_warn "Tip: open Xcode → Simulator, boot one, then re-run."
  fi
fi

# ── 4. Launch Flutter ─────────────────────────────────────────────────────────
log_info "Launching Flutter app…"
if [[ ${#FLUTTER_ARGS[@]} -gt 0 ]]; then
  log_info "flutter run args: ${FLUTTER_ARGS[*]}"
fi
echo ""

# Run flutter — 'true' prevents set -e from triggering cleanup prematurely
flutter run "${FLUTTER_ARGS[@]+"${FLUTTER_ARGS[@]}"}" || true

# cleanup() fires automatically via trap EXIT
