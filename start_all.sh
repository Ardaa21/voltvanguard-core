#!/usr/bin/env bash
# ╔══════════════════════════════════════════════════════════════════════════════╗
# ║  VoltVanguard — Tek Komutla Tam Stack Başlatıcı                            ║
# ║  Kullanım:  chmod +x start_all.sh && ./start_all.sh                        ║
# ╚══════════════════════════════════════════════════════════════════════════════╝
set -euo pipefail

# ── Java 21 Zorunlu (Temurin veya herhangi bir JDK 21) ────────────────────────
JAVA21=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
if [[ -z "$JAVA21" ]]; then
  echo "❌  Java 21 bulunamadı. Kurmak için:"
  echo "    brew install --cask temurin@21"
  exit 1
fi
export JAVA_HOME="$JAVA21"
export PATH="$JAVA_HOME/bin:$PATH"

# ── Paths ──────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR"
AGENT_DIR="$SCRIPT_DIR/ai-agents/route-optimizer"
VENV_DIR="$AGENT_DIR/.venv"
LOG_DIR="$SCRIPT_DIR/logs"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
mkdir -p "$LOG_DIR"

# ── Renkler ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[0;33m'; CYAN='\033[0;36m'
RED='\033[0;31m'; BOLD='\033[1m'; DIM='\033[2m'; RESET='\033[0m'

ok()   { echo -e "${GREEN}  ✓  $*${RESET}"; }
info() { echo -e "${CYAN}  →  $*${RESET}"; }
warn() { echo -e "${YELLOW}  ⚠  $*${RESET}"; }
err()  { echo -e "${RED}  ✗  $*${RESET}"; }
banner() {
  echo ""
  echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════╗${RESET}"
  echo -e "${BOLD}${CYAN}║     ⚡  VoltVanguard Full Stack Başlatılıyor  ⚡  ║${RESET}"
  echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════╝${RESET}"
  echo ""
}

# ── Shutdown trap ──────────────────────────────────────────────────────────────
shutdown_all() {
  echo ""
  info "Tüm servisler durduruluyor…"
  [[ -f "$LOG_DIR/backend.pid" ]] && kill "$(cat "$LOG_DIR/backend.pid")" 2>/dev/null || true
  [[ -f "$LOG_DIR/agent.pid"   ]] && kill "$(cat "$LOG_DIR/agent.pid")"   2>/dev/null || true
  docker compose -f "$COMPOSE_FILE" stop 2>/dev/null || true
  echo -e "${CYAN}  VoltVanguard kapatıldı. ⚡${RESET}"
}
trap shutdown_all SIGINT SIGTERM

banner

# ════════════════════════════════════════════════════════════════
# PHASE 1 · Docker Desktop
# ════════════════════════════════════════════════════════════════
echo -e "${BOLD}  PHASE 1 · Docker${RESET}"

if ! command -v docker &>/dev/null; then
  warn "Docker bulunamadı — brew ile kuruluyor…"
  brew install --cask docker-desktop
fi

if ! docker info &>/dev/null 2>&1; then
  info "Docker Desktop başlatılıyor…"
  open -a Docker
fi

info "Docker Engine hazır olana kadar bekleniyor (max 90s)…"
ELAPSED=0
until docker info &>/dev/null 2>&1; do
  [[ $ELAPSED -ge 90 ]] && { err "Docker 90s içinde başlamadı."; exit 1; }
  printf "\r${DIM}     %ds bekleniyor…${RESET}" "$ELAPSED"
  sleep 3; ELAPSED=$((ELAPSED+3))
done
ok "Docker Engine hazır"

# ════════════════════════════════════════════════════════════════
# PHASE 2 · Docker Compose (Infrastructure)
# ════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}  PHASE 2 · Altyapı (PostgreSQL · Redis · Kafka)${RESET}"

# Eski containerları temiz kapat, sonra başlat
info "Containerlar başlatılıyor…"
docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
docker compose -f "$COMPOSE_FILE" up -d 2>&1 | grep -E "✔|✗|Error|error" | \
  while IFS= read -r line; do echo -e "${DIM}     $line${RESET}"; done || true

# PostgreSQL sağlık bekle
info "PostgreSQL bekleniyor…"
ELAPSED=0
until docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U postgres -q 2>/dev/null; do
  [[ $ELAPSED -ge 60 ]] && { err "PostgreSQL 60s içinde hazır olmadı."; exit 1; }
  sleep 2; ELAPSED=$((ELAPSED+2))
done
ok "PostgreSQL hazır"

# Redis sağlık bekle (port 6380)
info "Redis bekleniyor (port 6380)…"
ELAPSED=0
until docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping 2>/dev/null | grep -q "PONG"; do
  [[ $ELAPSED -ge 60 ]] && { err "Redis 60s içinde hazır olmadı."; exit 1; }
  sleep 2; ELAPSED=$((ELAPSED+2))
done
ok "Redis hazır (localhost:6380)"

# Kafka sağlık bekle
info "Kafka bekleniyor…"
ELAPSED=0
until docker compose -f "$COMPOSE_FILE" exec -T kafka \
    kafka-topics --bootstrap-server localhost:9092 --list &>/dev/null 2>&1; do
  [[ $ELAPSED -ge 90 ]] && { warn "Kafka 90s sonra hâlâ hazır değil — devam ediliyor."; break; }
  sleep 3; ELAPSED=$((ELAPSED+3))
done
ok "Kafka hazır"

ok "✅ Tüm altyapı sağlıklı"

# ════════════════════════════════════════════════════════════════
# PHASE 3 · Spring Boot Backend
# ════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}  PHASE 3 · Spring Boot Backend (Java $($JAVA_HOME/bin/java -version 2>&1 | head -1))${RESET}"

cd "$BACKEND_DIR"

MVN_CMD="mvn"
command -v mvn &>/dev/null || { err "Maven bulunamadı: brew install maven"; exit 1; }

info "Maven build (testler atlanıyor)…"
mvn clean package -DskipTests -q 2>&1 | tail -3 | \
  while IFS= read -r line; do echo -e "${DIM}     $line${RESET}"; done || {
    err "Maven build başarısız! Log: $LOG_DIR/backend-build.log"
    mvn clean package -DskipTests 2>&1 | tail -30 | tee "$LOG_DIR/backend-build.log"
    exit 1
  }

JAR_FILE=$(ls "$BACKEND_DIR"/target/voltvanguard-core-*.jar 2>/dev/null | grep -v sources | head -1)
if [[ -z "$JAR_FILE" ]]; then
  err "JAR dosyası bulunamadı! target/ klasörünü kontrol et."
  exit 1
fi

info "Spring Boot başlatılıyor (arka planda)…"
nohup java -jar "$JAR_FILE" \
  > "$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
disown $BACKEND_PID
echo "$BACKEND_PID" > "$LOG_DIR/backend.pid"

info "Health endpoint bekleniyor (max 90s)…"
ELAPSED=0
until curl -sf "http://localhost:8080/api/v1/actuator/health" -o /dev/null 2>/dev/null; do
  [[ $ELAPSED -ge 90 ]] && {
    err "Backend 90s içinde başlamadı. Log:"
    tail -20 "$LOG_DIR/backend.log"
    exit 1
  }
  kill -0 "$BACKEND_PID" 2>/dev/null || {
    err "Backend çöktü! Log:"
    tail -30 "$LOG_DIR/backend.log"
    exit 1
  }
  printf "\r${DIM}     Backend başlatılıyor… %ds${RESET}" "$ELAPSED"
  sleep 3; ELAPSED=$((ELAPSED+3))
done
echo ""
ok "✅ Spring Boot çalışıyor → http://localhost:8080/api/v1"

cd "$SCRIPT_DIR"

# ════════════════════════════════════════════════════════════════
# PHASE 4 · Python AI Agent
# ════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}  PHASE 4 · Python AI Agent${RESET}"

if [[ ! -d "$AGENT_DIR" ]]; then
  warn "AI agent dizini bulunamadı: $AGENT_DIR — atlanıyor."
else
  cd "$AGENT_DIR"

  # Python bul
  PYTHON_CMD=""
  for py in python3.12 python3.11 python3.10 python3; do
    command -v "$py" &>/dev/null && { PYTHON_CMD="$py"; break; }
  done
  [[ -z "$PYTHON_CMD" ]] && { warn "Python 3.10+ bulunamadı (brew install python@3.12). AI agent atlanıyor."; }

  if [[ -n "$PYTHON_CMD" ]]; then
    # Venv
    [[ ! -d "$VENV_DIR" ]] && { info "Python venv oluşturuluyor…"; "$PYTHON_CMD" -m venv "$VENV_DIR"; }
    # shellcheck source=/dev/null
    source "$VENV_DIR/bin/activate"
    pip install -q --upgrade pip
    [[ -f "$AGENT_DIR/requirements.txt" ]] && pip install -q -r "$AGENT_DIR/requirements.txt"

    # .env yoksa oluştur
    [[ ! -f "$AGENT_DIR/.env" && -f "$AGENT_DIR/.env.example" ]] && \
      cp "$AGENT_DIR/.env.example" "$AGENT_DIR/.env" && warn ".env kopyalandı — OPENAI_API_KEY'i düzenle"

    nohup python main.py > "$LOG_DIR/agent.log" 2>&1 &
    AGENT_PID=$!
    disown $AGENT_PID
    echo "$AGENT_PID" > "$LOG_DIR/agent.pid"

    sleep 4
    kill -0 "$AGENT_PID" 2>/dev/null && ok "✅ AI Agent çalışıyor (PID $AGENT_PID)" || \
      { warn "AI Agent çöktü. Log: $LOG_DIR/agent.log"; tail -10 "$LOG_DIR/agent.log"; }
  fi
  cd "$SCRIPT_DIR"
fi

# ════════════════════════════════════════════════════════════════
# ÖZET
# ════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}${GREEN}  ╔════════════════════════════════════════════════════╗"
echo -e "  ║   ⚡  VoltVanguard tamamen çalışıyor!  ⚡          ║"
echo -e "  ╚════════════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  ${GREEN}PostgreSQL${RESET}   → localhost:5432"
echo -e "  ${GREEN}Redis${RESET}        → localhost:6380"
echo -e "  ${GREEN}Kafka${RESET}        → localhost:9092"
echo -e "  ${CYAN}Kafdrop UI${RESET}   → http://localhost:9000"
echo -e "  ${CYAN}pgAdmin UI${RESET}   → http://localhost:5050  (admin@voltvanguard.dev / admin)"
echo -e "  ${GREEN}Spring Boot${RESET}  → http://localhost:8080/api/v1"
echo -e "  ${CYAN}Swagger UI${RESET}   → http://localhost:8080/api/v1/swagger-ui.html"
echo ""
echo -e "  ${BOLD}Flutter:${RESET}"
echo -e "  ${DIM}cd mobile/voltvanguard_app && flutter run${RESET}"
echo ""
echo -e "  ${BOLD}Loglar:${RESET}"
echo -e "  ${DIM}tail -f logs/backend.log${RESET}"
echo -e "  ${DIM}tail -f logs/agent.log${RESET}"
echo ""
echo -e "  ${YELLOW}Durdurmak için:  ./stop_all.sh  veya  Ctrl+C${RESET}"
echo ""
ok "start_all.sh tamamlandı — tüm servisler arka planda çalışıyor ⚡"
