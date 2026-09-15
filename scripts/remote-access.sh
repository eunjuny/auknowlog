#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"
RUNTIME_DIR="${PROJECT_ROOT}/.runtime/remote-access"
AUTH_PROXY_SCRIPT="${SCRIPT_DIR}/tunnel-auth-proxy.mjs"
AUTH_PROXY_PID_FILE="${RUNTIME_DIR}/auth-proxy.pid"
CLOUDFLARED_PID_FILE="${RUNTIME_DIR}/cloudflared.pid"
AUTH_PROXY_LOG_FILE="${RUNTIME_DIR}/auth-proxy.log"
CLOUDFLARED_LOG_FILE="${RUNTIME_DIR}/cloudflared.log"
URL_FILE="${RUNTIME_DIR}/public-url"
CREDENTIALS_FILE="${RUNTIME_DIR}/credentials"
STOP_REQUESTED_FILE="${RUNTIME_DIR}/stop-requested"

FRONTEND_URL="${AUKNOWLOG_FRONTEND_URL:-http://127.0.0.1:5173}"
BACKEND_HEALTH_URL="${AUKNOWLOG_BACKEND_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
AUTH_PROXY_URL="http://127.0.0.1:4180"

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '오류: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "'$1' 명령을 찾을 수 없습니다. $2"
}

read_pid() {
  local pid_file="$1"
  [[ -f "${pid_file}" ]] || return 1
  tr -d '[:space:]' < "${pid_file}"
}

is_managed_process_running() {
  local pid_file="$1"
  local expected_command="$2"
  local pid
  local command_line

  pid="$(read_pid "${pid_file}")" || return 1
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  kill -0 "${pid}" 2>/dev/null || return 1
  command_line="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
  [[ "${command_line}" == *"${expected_command}"* ]]
}

stop_managed_process() {
  local label="$1"
  local pid_file="$2"
  local expected_command="$3"
  local pid
  local attempt

  if ! is_managed_process_running "${pid_file}" "${expected_command}"; then
    rm -f "${pid_file}"
    return
  fi

  pid="$(read_pid "${pid_file}")"
  kill "${pid}" 2>/dev/null || true

  for attempt in {1..20}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      break
    fi
    sleep 0.2
  done

  if kill -0 "${pid}" 2>/dev/null; then
    printf '경고: %s 프로세스(PID %s)가 아직 종료되지 않았습니다.\n' "${label}" "${pid}" >&2
  fi
  rm -f "${pid_file}"
}

stop_tunnel() {
  stop_managed_process "cloudflared" "${CLOUDFLARED_PID_FILE}" "cloudflared"
  stop_managed_process "인증 프록시" "${AUTH_PROXY_PID_FILE}" "tunnel-auth-proxy.mjs"
  rm -f "${URL_FILE}" "${CREDENTIALS_FILE}"
}

request_tunnel_stop() {
  mkdir -p "${RUNTIME_DIR}"
  : > "${STOP_REQUESTED_FILE}"
  stop_tunnel
}

show_status() {
  local auth_proxy_running="no"
  local cloudflared_running="no"

  if is_managed_process_running "${AUTH_PROXY_PID_FILE}" "tunnel-auth-proxy.mjs"; then
    auth_proxy_running="yes"
  fi
  if is_managed_process_running "${CLOUDFLARED_PID_FILE}" "cloudflared"; then
    cloudflared_running="yes"
  fi

  if [[ "${auth_proxy_running}" == "yes" && "${cloudflared_running}" == "yes" && -s "${URL_FILE}" ]]; then
    log "접속 모드: quick-tunnel"
    log "외부 URL: $(<"${URL_FILE}")"
    log "인증 프록시: 실행 중"
    log "Cloudflare 터널: 실행 중"
  else
    log "접속 모드: local"
    log "로컬 URL: ${FRONTEND_URL}"
    log "외부 공개: 꺼짐"
    if [[ "${auth_proxy_running}" == "yes" || "${cloudflared_running}" == "yes" ]]; then
      log "경고: 일부 터널 프로세스만 실행 중입니다. 'stop' 후 다시 시작하세요."
    fi
  fi
}

show_credentials() {
  [[ -s "${CREDENTIALS_FILE}" ]] || fail "활성 Quick Tunnel 인증 정보가 없습니다."
  # This file is mode 600 and excluded from Git. It is deleted when the tunnel stops.
  cat "${CREDENTIALS_FILE}"
}

wait_for_http_status() {
  local url="$1"
  local expected_status="$2"
  local session_cookie="${3:-}"
  local max_attempts="${4:-20}"
  local attempt
  local actual_status="000"
  local hostname=""
  local resolved_ip=""

  if [[ "${url}" =~ ^https://([^/]+\.trycloudflare\.com) ]]; then
    hostname="${BASH_REMATCH[1]}"
  fi

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    resolved_ip=""
    if [[ -n "${hostname}" ]] && command -v dig >/dev/null 2>&1; then
      resolved_ip="$(dig +short "${hostname}" A 2>/dev/null | awk '/^[0-9]+(\.[0-9]+){3}$/ { print; exit }')"
      if [[ -z "${resolved_ip}" ]]; then
        resolved_ip="$(dig @1.1.1.1 +short "${hostname}" A 2>/dev/null | awk '/^[0-9]+(\.[0-9]+){3}$/ { print; exit }')"
      fi
    fi

    # --resolve bypasses a stale negative entry in the macOS curl DNS cache during first-time validation.
    if [[ -n "${session_cookie}" && -n "${resolved_ip}" ]]; then
      actual_status="$(curl --resolve "${hostname}:443:${resolved_ip}" --silent --output /dev/null --max-time 5 --write-out '%{http_code}' --header "Cookie: ${session_cookie}" "${url}" || true)"
    elif [[ -n "${session_cookie}" ]]; then
      actual_status="$(curl --silent --output /dev/null --max-time 5 --write-out '%{http_code}' --header "Cookie: ${session_cookie}" "${url}" || true)"
    elif [[ -n "${resolved_ip}" ]]; then
      actual_status="$(curl --resolve "${hostname}:443:${resolved_ip}" --silent --output /dev/null --max-time 5 --write-out '%{http_code}' "${url}" || true)"
    else
      actual_status="$(curl --silent --output /dev/null --max-time 5 --write-out '%{http_code}' "${url}" || true)"
    fi
    if [[ "${actual_status}" == "${expected_status}" ]]; then
      return 0
    fi
    sleep 1
  done
  printf '기대 HTTP 상태 %s, 마지막 응답 %s: %s\n' "${expected_status}" "${actual_status}" "${url}" >&2
  return 1
}

wait_for_public_url() {
  local attempt
  local public_url

  for attempt in {1..30}; do
    public_url="$(rg --only-matching 'https://[-a-z0-9]+\.trycloudflare\.com' "${CLOUDFLARED_LOG_FILE}" 2>/dev/null | head -n 1 || true)"
    if [[ -n "${public_url}" ]]; then
      printf '%s' "${public_url}"
      return 0
    fi
    if ! is_managed_process_running "${CLOUDFLARED_PID_FILE}" "cloudflared"; then
      return 1
    fi
    sleep 1
  done
  return 1
}

start_quick_tunnel() {
  local send_access_email="${1:-no}"
  local username
  local password
  local session_token
  local session_cookie
  local public_url
  local auth_proxy_pid
  local cloudflared_pid
  local shutdown_requested="no"
  local max_lifetime_seconds="${AUKNOWLOG_TUNNEL_MAX_SECONDS:-28800}"
  local started_at_seconds

  trap 'stop_tunnel >/dev/null 2>&1 || true' EXIT

  require_command "node" "프로젝트 실행에 사용하는 Node.js가 필요합니다."
  require_command "cloudflared" "macOS에서는 'brew install cloudflared'로 설치할 수 있습니다."
  require_command "curl" "curl이 필요합니다."
  require_command "openssl" "안전한 임시 비밀번호 생성에 openssl이 필요합니다."
  require_command "rg" "Quick Tunnel URL 확인에 ripgrep(rg)이 필요합니다."
  [[ "${max_lifetime_seconds}" =~ ^[0-9]+$ && "${max_lifetime_seconds}" -ge 60 ]] ||
    fail "AUKNOWLOG_TUNNEL_MAX_SECONDS는 60 이상의 정수여야 합니다."

  if is_managed_process_running "${AUTH_PROXY_PID_FILE}" "tunnel-auth-proxy.mjs" || is_managed_process_running "${CLOUDFLARED_PID_FILE}" "cloudflared"; then
    fail "이미 터널 프로세스가 실행 중입니다. 먼저 './scripts/remote-access.sh stop'을 실행하세요."
  fi

  curl --fail --silent --show-error --max-time 5 "${FRONTEND_URL}" >/dev/null ||
    fail "프론트엔드가 ${FRONTEND_URL}에서 실행 중이지 않습니다."
  curl --fail --silent --show-error --max-time 5 "${BACKEND_HEALTH_URL}" >/dev/null ||
    fail "백엔드 Health API가 ${BACKEND_HEALTH_URL}에서 응답하지 않습니다."

  node "${SCRIPT_DIR}/tunnel-auth-proxy.test.mjs" >/dev/null 2>&1 ||
    fail "인증 프록시 자체 테스트에 실패했습니다."

  username="${AUKNOWLOG_TUNNEL_USERNAME:-eunjuny}"
  username="$(printf '%s' "${username}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [[ "${username}" =~ ^[A-Za-z0-9._-]+$ ]] ||
    fail "사용자명에는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다."

  password="${AUKNOWLOG_TUNNEL_PASSWORD:-$(openssl rand -hex 16)}"
  password="$(printf '%s' "${password}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  [[ ${#password} -ge 20 ]] || fail "터널 비밀번호는 20자 이상이어야 합니다."
  [[ "${password}" != *:* ]] || fail "터널 비밀번호에는 콜론(:)을 사용할 수 없습니다."
  session_token="$(openssl rand -hex 32)"
  session_cookie="auknowlog_tunnel_session=${session_token}"

  mkdir -p "${RUNTIME_DIR}"
  chmod 700 "${RUNTIME_DIR}"
  rm -f "${AUTH_PROXY_PID_FILE}" "${CLOUDFLARED_PID_FILE}" "${URL_FILE}" "${CREDENTIALS_FILE}" \
    "${AUTH_PROXY_LOG_FILE}" "${CLOUDFLARED_LOG_FILE}" "${STOP_REQUESTED_FILE}"

  umask 077
  # 값을 라벨 다음 줄에 단독으로 출력해 드래그·더블클릭 복사 시 앞 공백이 섞이지 않게 한다.
  printf '사용자명:\n%s\n비밀번호:\n%s\n' "${username}" "${password}" > "${CREDENTIALS_FILE}"

  nohup env \
    AUKNOWLOG_TUNNEL_USERNAME="${username}" \
    AUKNOWLOG_TUNNEL_PASSWORD="${password}" \
    AUKNOWLOG_TUNNEL_SESSION_TOKEN="${session_token}" \
    AUKNOWLOG_TUNNEL_UPSTREAM="http://127.0.0.1:5173" \
    node "${AUTH_PROXY_SCRIPT}" >"${AUTH_PROXY_LOG_FILE}" 2>&1 &
  auth_proxy_pid=$!
  printf '%s\n' "${auth_proxy_pid}" > "${AUTH_PROXY_PID_FILE}"

  wait_for_http_status "${AUTH_PROXY_URL}" "302" ||
    fail "인증 프록시의 미인증 로그인 전환(302) 검증에 실패했습니다. ${AUTH_PROXY_LOG_FILE}을 확인하세요."
  wait_for_http_status "${AUTH_PROXY_URL}" "200" "${session_cookie}" ||
    fail "인증 프록시의 인증 성공(200) 검증에 실패했습니다. ${AUTH_PROXY_LOG_FILE}을 확인하세요."
  is_managed_process_running "${AUTH_PROXY_PID_FILE}" "tunnel-auth-proxy.mjs" ||
    fail "시작한 인증 프록시 프로세스가 종료되었습니다. 포트 충돌 여부와 ${AUTH_PROXY_LOG_FILE}을 확인하세요."

  nohup cloudflared tunnel \
    --config /dev/null \
    --no-autoupdate \
    --metrics 127.0.0.1:0 \
    --url "${AUTH_PROXY_URL}" \
    >"${CLOUDFLARED_LOG_FILE}" 2>&1 &
  cloudflared_pid=$!
  printf '%s\n' "${cloudflared_pid}" > "${CLOUDFLARED_PID_FILE}"

  public_url="$(wait_for_public_url)" ||
    fail "Quick Tunnel URL을 생성하지 못했습니다. ${CLOUDFLARED_LOG_FILE}을 확인하세요."
  printf '%s\n' "${public_url}" > "${URL_FILE}"

  # A newly-issued trycloudflare.com hostname can need propagation time.
  wait_for_http_status "${public_url}" "302" "" "60" ||
    fail "공개 URL의 미인증 로그인 전환(302) 검증에 실패했습니다."
  wait_for_http_status "${public_url}" "200" "${session_cookie}" "20" ||
    fail "공개 URL의 인증 성공(200) 검증에 실패했습니다."

  if [[ "${send_access_email}" == "yes" ]]; then
    node "${SCRIPT_DIR}/send-remote-access-email.mjs" \
      --lifetime-seconds "${max_lifetime_seconds}" ||
      fail "원격 접속 정보 이메일 전송에 실패했습니다. 보안을 위해 시작한 터널을 종료합니다."
    log "Quick Tunnel을 시작하고 인증 동작을 검증했습니다."
    log "원격 URL과 임시 인증 정보는 설정된 수신 메일에서 확인하세요."
  else
    log "Quick Tunnel을 시작하고 인증 동작을 검증했습니다."
    log "외부 URL: ${public_url}"
    show_credentials
  fi
  log "자동 종료: $((max_lifetime_seconds / 3600))시간 후"
  log "이 터미널을 유지합니다. 종료하려면 Ctrl+C 또는 다른 터미널에서 './scripts/remote-access.sh stop'을 실행하세요."

  started_at_seconds="${SECONDS}"
  trap 'shutdown_requested="yes"' INT TERM
  while [[ "${shutdown_requested}" == "no" ]] \
    && is_managed_process_running "${AUTH_PROXY_PID_FILE}" "tunnel-auth-proxy.mjs" \
    && is_managed_process_running "${CLOUDFLARED_PID_FILE}" "cloudflared"; do
    if ((SECONDS - started_at_seconds >= max_lifetime_seconds)); then
      shutdown_requested="expired"
      break
    fi
    sleep 2
  done

  if [[ "${shutdown_requested}" == "expired" ]]; then
    log "설정된 최대 사용 시간이 지나 Quick Tunnel을 자동 종료했습니다."
    return 0
  fi
  if [[ "${shutdown_requested}" == "yes" || -e "${STOP_REQUESTED_FILE}" ]]; then
    [[ ! -e "${STOP_REQUESTED_FILE}" ]] || unlink "${STOP_REQUESTED_FILE}"
    log "Quick Tunnel을 종료했습니다."
    return 0
  fi
  fail "터널 프로세스가 예기치 않게 종료되었습니다. 로그는 ${RUNTIME_DIR}에서 확인하세요."
}

show_menu() {
  local choice

  [[ -t 0 ]] || {
    usage
    exit 1
  }

  log "접속 모드를 선택하세요."
  log "1) local         - 외부 공개 끄기"
  log "2) quick-tunnel  - 임시 인증 URL 열기"
  log "3) quick-email   - 임시 인증 URL을 열고 접속 정보를 메일로 보내기"
  log "4) status        - 현재 상태 확인"
  log "5) stop          - 외부 공개 종료"
  read -r -p "> " choice

  case "${choice}" in
    1) request_tunnel_stop; show_status ;;
    2) start_quick_tunnel ;;
    3) start_quick_tunnel "yes" ;;
    4) show_status ;;
    5) request_tunnel_stop; show_status ;;
    *) fail "올바른 번호를 선택하세요." ;;
  esac
}

usage() {
  cat <<'USAGE'
사용법: ./scripts/remote-access.sh [명령]

명령:
  local          외부 터널을 종료하고 로컬 전용 모드로 전환
  quick-tunnel   로그인·세션 인증 프록시와 Cloudflare Quick Tunnel 시작
  quick-email    Quick Tunnel을 인증 검증한 뒤 접속 정보를 설정된 수신 메일로 1회 전송
  status         현재 접속 모드와 URL 확인
  credentials    활성 터널의 임시 사용자명과 비밀번호 확인
  stop           Quick Tunnel과 인증 프록시 종료
  help           도움말 표시

명령 없이 실행하면 선택 메뉴를 표시합니다.
USAGE
}

case "${1:-menu}" in
  local)
    request_tunnel_stop
    show_status
    ;;
  quick-tunnel|quick)
    start_quick_tunnel
    ;;
  quick-tunnel-email|quick-email)
    start_quick_tunnel "yes"
    ;;
  status)
    show_status
    ;;
  credentials)
    show_credentials
    ;;
  stop)
    request_tunnel_stop
    show_status
    ;;
  help|-h|--help)
    usage
    ;;
  menu)
    show_menu
    ;;
  *)
    usage
    exit 1
    ;;
esac
