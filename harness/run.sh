#!/usr/bin/env bash
# Wearly 앱↔백엔드 연동 하네스 실행기 (doc/05_백엔드_API.md 9장)
#
#   ./run.sh                      DB 리셋 → 백엔드(18080) 기동 → 시나리오 → 백엔드 종료
#   ./run.sh --keep               끝나도 백엔드를 남긴다
#   ./run.sh --external <url>     DB 리셋·기동 없이 그 서버로 시나리오만 (TTL 의존 시나리오는 건너뜀)
#   ./run.sh --db-only            하네스 DB(wearly_harness)만 다시 만들고 끝낸다
#   ./run.sh --only 토큰,AI       이름·분류에 키워드가 들어간 시나리오만 (러너로 그대로 넘김)
#   ./run.sh --list               시나리오 목록만 보기
set -euo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$HARNESS_DIR/.." && pwd)"
BACKEND_DIR="${HARNESS_BACKEND_DIR:-$ROOT_DIR/backend}"
LOG_DIR="$HARNESS_DIR/.logs"
LOG_FILE="$LOG_DIR/backend.log"

PORT=18080
DB_CONTAINER=wearly-postgres
DB_NAME=wearly_harness
BOOT_TIMEOUT="${HARNESS_BOOT_TIMEOUT:-180}"
DEFAULT_JAVA_HOME=/Users/showzone/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home

EXTERNAL_URL=""
KEEP=0
DB_ONLY=0
LIST_ONLY=0
RUNNER_ARGS=()

BACKEND_PID=""

# ── 출력 ──
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YLW=$'\033[33m'; C_DIM=$'\033[2m'; C_B=$'\033[1m'; C_0=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_YLW=""; C_DIM=""; C_B=""; C_0=""
fi
step() { echo "${C_B}▶ $*${C_0}"; }
info() { echo "  $*"; }
warn() { echo "${C_YLW}  ! $*${C_0}"; }
die() { echo "${C_RED}✗ $*${C_0}" >&2; exit 1; }

usage() {
  sed -n '2,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  cat <<EOF

환경변수
  HARNESS_JAVA_HOME     백엔드를 띄울 JDK 21 (기본: $DEFAULT_JAVA_HOME)
  HARNESS_BOOT_TIMEOUT  백엔드가 UP이 될 때까지 기다릴 초 (기본: 180)
  HARNESS_BACKEND_DIR   띄울 백엔드 디렉터리 (기본: 저장소 루트의 backend/)
EOF
}

# ── 인자 ──
while [[ $# -gt 0 ]]; do
  case "$1" in
    --external)
      [[ $# -ge 2 && -n "$2" ]] || die "--external 뒤에 서버 주소를 주세요. 예: --external http://localhost:8080"
      EXTERNAL_URL="${2%/}"; shift 2 ;;
    --keep) KEEP=1; shift ;;
    --db-only) DB_ONLY=1; shift ;;
    --list) LIST_ONLY=1; shift ;;
    --only)
      [[ $# -ge 2 ]] || die "--only 뒤에 키워드를 주세요. 예: --only 토큰,AI"
      RUNNER_ARGS+=("--only" "$2"); shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "알 수 없는 옵션: $1 (./run.sh --help)" ;;
  esac
done

TSX="$HARNESS_DIR/node_modules/.bin/tsx"

run_scenarios() {
  # $1 = 서버 주소, 나머지 = 러너 추가 인자
  local url="$1"; shift
  local status=0
  (cd "$HARNESS_DIR" && "$TSX" src/main.ts --base-url "$url" "$@" ${RUNNER_ARGS[@]+"${RUNNER_ARGS[@]}"}) || status=$?
  return "$status"
}

require_node_modules() {
  [[ -x "$TSX" ]] || die "하네스 의존성이 없습니다. 먼저 실행하세요:  cd harness && npm install"
}

# ── DB ──
container_state() {
  docker inspect -f '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$DB_CONTAINER" 2>/dev/null || echo "missing/none"
}

ensure_db() {
  step "DB 컨테이너 확인 ($DB_CONTAINER)"
  command -v docker >/dev/null 2>&1 || die "docker 명령이 없습니다. Docker Desktop을 설치해 주세요."
  docker info >/dev/null 2>&1 || die "Docker가 꺼져 있습니다. Docker Desktop을 켠 뒤 다시 실행해 주세요."

  local state
  state="$(container_state)"
  if [[ "$state" != "running/healthy" ]]; then
    info "현재 상태: $state → docker compose up -d"
    (cd "$ROOT_DIR" && docker compose up -d db) >/dev/null
    local waited=0
    while :; do
      state="$(container_state)"
      [[ "$state" == "running/healthy" ]] && break
      if (( waited >= 60 )); then
        docker logs --tail 30 "$DB_CONTAINER" 2>&1 || true
        die "DB가 60초 안에 healthy가 되지 않았습니다 (상태: $state)."
      fi
      sleep 1; waited=$((waited + 1))
    done
  fi
  info "${C_GRN}healthy${C_0}"
}

reset_db() {
  step "하네스 DB 초기화 ($DB_NAME — 개발용 'wearly' DB는 건드리지 않음)"
  docker exec -e PGOPTIONS="-c client_min_messages=warning" "$DB_CONTAINER" psql -U wearly -d postgres -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE);" \
    -c "CREATE DATABASE $DB_NAME OWNER wearly;" >/dev/null
  local exists
  exists="$(docker exec "$DB_CONTAINER" psql -U wearly -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")"
  [[ "$exists" == "1" ]] || die "$DB_NAME 생성을 확인하지 못했습니다."
  info "${C_GRN}$DB_NAME 새로 만듦${C_0}"
}

# ── 백엔드 ──
listening_pids() {
  lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null || true
}

backend_alive() {
  [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null
}

stop_backend() {
  [[ -n "$BACKEND_PID" ]] || return 0
  step "백엔드 종료"
  # bootRun은 Gradle → 앱 JVM으로 자식 프로세스를 만든다. 프로세스 그룹 전체와 포트 점유 프로세스를 함께 끈다.
  kill -TERM -- "-$BACKEND_PID" 2>/dev/null || kill -TERM "$BACKEND_PID" 2>/dev/null || true
  local pids
  pids="$(listening_pids)"
  [[ -n "$pids" ]] && kill -TERM $pids 2>/dev/null || true
  local waited=0
  while (( waited < 20 )); do
    if ! backend_alive && [[ -z "$(listening_pids)" ]]; then break; fi
    sleep 0.5; waited=$((waited + 1))
  done
  if backend_alive || [[ -n "$(listening_pids)" ]]; then
    warn "10초 안에 끝나지 않아 강제 종료합니다."
    kill -KILL -- "-$BACKEND_PID" 2>/dev/null || kill -KILL "$BACKEND_PID" 2>/dev/null || true
    pids="$(listening_pids)"
    [[ -n "$pids" ]] && kill -KILL $pids 2>/dev/null || true
  fi
  info "종료됨"
  BACKEND_PID=""
}

on_exit() {
  local code=$?
  trap - EXIT INT TERM
  if [[ -n "$BACKEND_PID" ]]; then
    if (( KEEP )); then
      echo ""
      echo "${C_YLW}--keep: 백엔드를 남겨 둡니다. http://localhost:$PORT  (로그: $LOG_FILE)${C_0}"
      echo "  끄기: kill -- -$BACKEND_PID   (안 꺼지면: lsof -t -iTCP:$PORT -sTCP:LISTEN | xargs kill)"
      echo "  이 서버로 다시 돌리기: ./run.sh --external http://localhost:$PORT"
    else
      stop_backend || true
    fi
  fi
  exit "$code"
}
trap on_exit EXIT
trap 'echo ""; warn "중단됨 (Ctrl+C)"; exit 130' INT
trap 'exit 143' TERM

ensure_port_free() {
  local busy
  busy="$(listening_pids)"
  if [[ -n "$busy" ]]; then
    die "포트 $PORT 를 이미 쓰는 프로세스가 있습니다 (PID: $(echo $busy)). 이전 --keep 백엔드라면 끄거나 './run.sh --external http://localhost:$PORT'로 실행하세요."
  fi
}

resolve_java_home() {
  local jh="${HARNESS_JAVA_HOME:-$DEFAULT_JAVA_HOME}"
  if [[ ! -x "$jh/bin/java" ]]; then
    jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  fi
  [[ -n "$jh" && -x "$jh/bin/java" ]] || die "JDK 21을 찾지 못했습니다. HARNESS_JAVA_HOME=<JDK 21 경로> 로 지정해 주세요."
  echo "$jh"
}

start_backend() {
  local java_home
  java_home="$(resolve_java_home)"
  mkdir -p "$LOG_DIR"
  step "백엔드 기동 (./gradlew bootRun, 포트 $PORT, JDK: $java_home)"
  info "${C_DIM}로그: $LOG_FILE${C_0}"
  # set -m: 백그라운드 작업을 별도 프로세스 그룹으로 띄워 종료 시 그룹째 끈다 (PID = PGID)
  set -m
  (
    cd "$BACKEND_DIR"
    unset ANTHROPIC_API_KEY
    # 개발용 backend/config/application-local.yml(실제 AI 키·관리자 계정·마스터 키)을 읽지 않게 없는 경로를 준다
    exec env \
      WEARLY_LOCAL_CONFIG="$HARNESS_DIR/.logs/no-local-config.yml" \
      JAVA_HOME="$java_home" \
      SERVER_PORT="$PORT" \
      SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/$DB_NAME" \
      SPRING_DATASOURCE_USERNAME=wearly \
      SPRING_DATASOURCE_PASSWORD=wearly \
      WEARLY_ACCESS_TTL=PT3S \
      WEARLY_AI_DAILY_LIMIT=3 \
      WEARLY_AI_PROVIDER=mock \
      WEARLY_ADMIN_EMAIL=admin@harness.wearly.test \
      WEARLY_ADMIN_PASSWORD=HarnessAdmin1234 \
      WEARLY_ADMIN_NAME=하네스관리자 \
      WEARLY_JWT_SECRET="${WEARLY_JWT_SECRET:-harness-only-jwt-secret-0123456789-abcdefghijklmnop}" \
      ./gradlew bootRun --console=plain --no-daemon
  ) >"$LOG_FILE" 2>&1 &
  BACKEND_PID=$!
  set +m

  local url="http://localhost:$PORT/actuator/health"
  local waited=0
  while :; do
    if curl -fsS --max-time 2 "$url" 2>/dev/null | grep -q '"status" *: *"UP"'; then
      break
    fi
    if ! backend_alive; then
      echo ""
      echo "${C_DIM}── backend.log 마지막 40줄 ──${C_0}"
      tail -n 40 "$LOG_FILE" || true
      BACKEND_PID=""
      die "백엔드 프로세스가 UP 전에 끝났습니다. 전체 로그: $LOG_FILE"
    fi
    if (( waited >= BOOT_TIMEOUT )); then
      echo ""
      echo "${C_DIM}── backend.log 마지막 40줄 ──${C_0}"
      tail -n 40 "$LOG_FILE" || true
      die "백엔드가 ${BOOT_TIMEOUT}초 안에 UP이 되지 않았습니다. 전체 로그: $LOG_FILE  (첫 실행이라 Gradle 내려받기가 길면 HARNESS_BOOT_TIMEOUT=600)"
    fi
    if (( waited > 0 && waited % 15 == 0 )); then
      info "${C_DIM}${waited}초 대기 중… $(tail -n 1 "$LOG_FILE" 2>/dev/null | cut -c1-100)${C_0}"
    fi
    sleep 1; waited=$((waited + 1))
  done
  info "${C_GRN}UP${C_0} (${waited}초)"
}

# ── 실행 ──
if (( LIST_ONLY )); then
  require_node_modules
  (cd "$HARNESS_DIR" && "$TSX" src/main.ts --list ${RUNNER_ARGS[@]+"${RUNNER_ARGS[@]}"})
  exit 0
fi

if (( DB_ONLY )); then
  ensure_db
  reset_db
  exit 0
fi

require_node_modules

if [[ -n "$EXTERNAL_URL" ]]; then
  (( KEEP )) && warn "--external에서는 --keep이 의미가 없습니다 (하네스가 띄운 백엔드가 없음)."
  step "외부 서버로 시나리오 실행: $EXTERNAL_URL"
  warn "DB 리셋·백엔드 기동을 건너뜁니다. Access TTL 3초에 기대는 시나리오는 건너뛰고, AI 한도는 서버 값을 따릅니다."
  run_scenarios "$EXTERNAL_URL" --external
  exit $?
fi

if [[ ! -d "$BACKEND_DIR" ]]; then
  cat >&2 <<EOF
${C_RED}✗ backend/ 디렉터리가 없습니다 (백엔드가 아직 이 저장소에 없음): $BACKEND_DIR${C_0}

  하네스는 저장소 루트의 backend/(Spring Boot, Gradle wrapper)를 직접 띄워 시험합니다.
  - 백엔드를 아직 받지 않았다면: git pull 로 backend/ 를 가져온 뒤 다시 실행하세요.
  - 다른 곳에서 백엔드가 떠 있다면: ./run.sh --external http://localhost:8080
  - DB만 준비해 두려면: ./run.sh --db-only
EOF
  exit 1
fi
[[ -x "$BACKEND_DIR/gradlew" ]] || die "backend/gradlew 가 없거나 실행 권한이 없습니다. (chmod +x backend/gradlew)"

resolve_java_home >/dev/null
ensure_port_free
ensure_db
reset_db
start_backend

step "시나리오 실행"
status=0
run_scenarios "http://localhost:$PORT" || status=$?
exit "$status"
