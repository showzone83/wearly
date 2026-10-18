#!/bin/zsh
# 웨어리 로컬 개발 한 번에 띄우기 (Mac 전용, doc/15).
# docker compose(db·valkey·minio·backend·admin)를 띄우고, Terminal 창으로 백엔드 로그·DB 실시간 로그·Expo(Metro)를 연다.
#
#   ./dev.sh              전체 빌드·실행 + 로그 창 2개 + Expo 창
#   ./dev.sh --no-build   이미지를 다시 빌드하지 않고 띄운다 (코드를 안 바꿨을 때 빠르다)
#   ./dev.sh --no-expo    Expo 창은 열지 않는다
#   ./dev.sh --ios        Expo를 iOS 시뮬레이터와 함께 연다 (expo start --ios)
#   ./dev.sh logs         로그 창 2개만 다시 연다
#   ./dev.sh stop         docker compose down (데이터 볼륨은 남는다)
set -e
ROOT=${0:A:h}
cd "$ROOT"

BUILD=--build
EXPO=1
EXPO_ARGS=""
CMD=up
for a in "$@"; do
  case $a in
    --no-build) BUILD="" ;;
    --no-expo) EXPO=0 ;;
    --ios) EXPO_ARGS="--ios" ;;
    logs) CMD=logs ;;
    stop) CMD=stop ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "모르는 옵션: $a (./dev.sh --help)"; exit 1 ;;
  esac
done

# Terminal 새 창에서 명령 실행. $1 = 창 제목, $2 = 명령
open_window() {
  local cmd="cd '$ROOT' && printf '\\\\e]0;$1\\\\a' && $2"
  osascript -e "tell application \"Terminal\" to do script \"${cmd//\"/\\\"}\"" >/dev/null
}

open_logs() {
  open_window "Wearly 백엔드 로그" "docker compose logs -f --since 1m backend"
  # 같은 실시간 로그가 이미 떠 있으면 하나 더 띄우지 않는다 (같은 기록이 두 번 찍힌다)
  if pgrep -f 'backend/scripts/live-log.py' >/dev/null; then
    echo "· DB 실시간 로그는 이미 떠 있어요"
  else
    open_window "Wearly 실시간 로그 (DB)" "python3 -I backend/scripts/live-log.py"
  fi
}

if [[ $CMD == stop ]]; then
  docker compose down
  exit 0
fi

if [[ $CMD == logs ]]; then
  open_logs
  exit 0
fi

docker info >/dev/null 2>&1 || { echo "Docker가 꺼져 있어요. Docker Desktop을 먼저 켜 주세요."; exit 1; }

echo "▶ docker compose up -d $BUILD"
docker compose up -d $BUILD

printf "▶ 백엔드 준비 기다리는 중"
for i in {1..100}; do
  if curl -sf http://localhost:8080/actuator/health >/dev/null; then break; fi
  printf "."; sleep 3
  if (( i == 100 )); then echo "\n백엔드가 5분 안에 뜨지 않았어요. docker compose logs backend 로 확인하세요."; exit 1; fi
done
echo " 완료"

open_logs

if (( EXPO )); then
  if lsof -nP -iTCP:8081 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "· Metro(8081)가 이미 떠 있어 Expo 창은 열지 않았어요"
  else
    # 시뮬레이터용 Xcode 경로 (CLAUDE.md: xcode-select가 Command Line Tools를 가리킨다)
    open_window "Wearly Expo" "cd mobile && DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer npx expo start $EXPO_ARGS"
  fi
fi

cat <<EOF

웨어리 로컬 실행 중
  백엔드 API      http://localhost:8080
  관리자 사이트    http://localhost:5173
  사진 저장소 콘솔  http://localhost:9001  (wearly / wearly-minio-local)
  DB              localhost:5433 · Valkey localhost:6380
중지: ./dev.sh stop
EOF
