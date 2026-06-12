#!/usr/bin/env bash
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  VoltVanguard — Stop All Services                                          ║
# ║  Kills Spring Boot + Python Agent, stops Docker containers.               ║
# ║  Usage: ./stop_all.sh                                                      ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'
BOLD='\033[1m'; DIM='\033[2m'; RESET='\033[0m'

echo ""
echo -e "${BOLD}${CYAN}  ⚡  VoltVanguard — Stopping all services…${RESET}"
echo ""

# ── Spring Boot ────────────────────────────────────────────────────────────────
PID_FILE="$LOG_DIR/backend.pid"
if [[ -f "$PID_FILE" ]]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    echo -e "${DIM}  Stopping Spring Boot (PID $PID)…${RESET}"
    kill -SIGTERM "$PID" 2>/dev/null || true
    # Wait up to 10 s for graceful shutdown
    for _ in {1..10}; do
      kill -0 "$PID" 2>/dev/null || break
      sleep 1
    done
    kill -0 "$PID" 2>/dev/null && kill -9 "$PID" 2>/dev/null || true
    echo -e "${GREEN}  🟢  Spring Boot stopped${RESET}"
  else
    echo -e "${DIM}  Spring Boot was not running (stale PID $PID)${RESET}"
  fi
  rm -f "$PID_FILE"
else
  # Fallback: find by port
  PORT_PID=$(lsof -ti tcp:8080 2>/dev/null || true)
  if [[ -n "$PORT_PID" ]]; then
    echo -e "${DIM}  Killing process on port 8080 (PID $PORT_PID)…${RESET}"
    kill -SIGTERM $PORT_PID 2>/dev/null || true
    echo -e "${GREEN}  🟢  Spring Boot stopped${RESET}"
  else
    echo -e "${DIM}  Spring Boot: not running${RESET}"
  fi
fi

# ── Python AI Agent ────────────────────────────────────────────────────────────
PID_FILE="$LOG_DIR/agent.pid"
if [[ -f "$PID_FILE" ]]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    echo -e "${DIM}  Stopping AI agent (PID $PID)…${RESET}"
    kill -SIGTERM "$PID" 2>/dev/null || true
    sleep 2
    kill -0 "$PID" 2>/dev/null && kill -9 "$PID" 2>/dev/null || true
    echo -e "${GREEN}  🟢  AI agent stopped${RESET}"
  else
    echo -e "${DIM}  AI agent was not running (stale PID $PID)${RESET}"
  fi
  rm -f "$PID_FILE"
else
  # Fallback: find by script name
  AGENT_PID=$(pgrep -f "python.*main.py" 2>/dev/null || true)
  if [[ -n "$AGENT_PID" ]]; then
    kill -SIGTERM $AGENT_PID 2>/dev/null || true
    echo -e "${GREEN}  🟢  AI agent stopped${RESET}"
  else
    echo -e "${DIM}  AI agent: not running${RESET}"
  fi
fi

# ── Docker infrastructure ──────────────────────────────────────────────────────
if [[ -f "$COMPOSE_FILE" ]] && command -v docker &>/dev/null; then
  echo -e "${DIM}  Stopping Docker containers (data preserved)…${RESET}"
  docker compose -f "$COMPOSE_FILE" stop 2>/dev/null || true
  echo -e "${GREEN}  🟢  Docker containers stopped${RESET}"
fi

echo ""
echo -e "${CYAN}${BOLD}  VoltVanguard stopped. Run ./start_all.sh to restart. ⚡${RESET}"
echo ""
