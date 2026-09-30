#!/usr/bin/env bash
set -Eeuo pipefail

backend_url="http://127.0.0.1:${BACKEND_PORT:-8080}"
frontend_url="http://127.0.0.1:${FRONTEND_PORT:-5173}"

check_status() {
  local expected="$1"
  local url="$2"
  local actual
  actual="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 "$url")"
  [[ "$actual" == "$expected" ]] || {
    printf 'HTTP 검증 실패: 예상 %s, 실제 %s\n' "$expected" "$actual" >&2
    return 1
  }
}

check_status 200 "$backend_url/actuator/health"
check_status 200 "$frontend_url/"
check_status 200 "$frontend_url/api/dashboard"
check_status 404 "$frontend_url/api/notifications/remote-access/email"
printf 'Compose 백엔드·프론트엔드·API 프록시·로컬 전용 메일 경계 검증 완료\n'
