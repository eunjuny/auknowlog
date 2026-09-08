# 외부 접속 모드 운영 가이드

구성 요소와 인증 요청 흐름을 그림으로 먼저 확인하려면 [외부 접속 구조와 동작 흐름](REMOTE_ACCESS_FLOW.html)을 참고한다.

## 목표

개발 환경은 기본적으로 로컬에서만 접근할 수 있어야 한다. 외부 확인이 필요한 시간에만 명시적으로 Quick Tunnel을 선택하고, 모든 공개 요청은 인증 프록시를 통과해야 한다.

애플리케이션 화면에 터널 스위치를 두지 않고 로컬 실행 스크립트로 분리했다. 외부 HTTP 요청이 자신의 공개 범위를 변경할 수 없게 하고, 개발자가 터널의 시작과 종료 시점을 명시적으로 통제하기 위해서다.

## 구성

```text
모바일 브라우저
  -> Cloudflare 임시 HTTPS URL
  -> cloudflared (외부에서 들어오는 포트는 열지 않음)
  -> 로그인·세션 인증 프록시 (127.0.0.1:4180)
  -> Vite (127.0.0.1:5173)
  -> /api 프록시
  -> Spring Boot (127.0.0.1:8080)
  -> PostgreSQL (외부 공개 안 함)
```

| 구성 요소 | 선택 이유 |
| --- | --- |
| cloudflared Quick Tunnel | 공인 IP, 포트 포워딩, 도메인 없이 일시적인 HTTPS URL을 만든다. |
| Node.js 인증 프록시 | 로그인 폼, 세션 쿠키, 실패 횟수 제한과 HTTP/WebSocket 역방향 프록시를 외부 의존성 없이 제공한다. |
| 실행 스크립트 | `local`을 안전한 기본값으로 유지하고 시작·상태·종료 과정을 반복 가능하게 만든다. |

Quick Tunnel은 인증을 제공하지 않는다. 따라서 `cloudflared`가 인증 프록시의 `4180` 포트만 바라보게 하고, 세션이 검증된 요청만 Vite로 전달한다. 인증 쿠키와 `Authorization` 헤더는 Vite와 Spring Boot로 전달하지 않는다.

초기 검증에서는 Caddy Basic Auth도 비교했다. 현재 Quick Tunnel 경로에서 Basic Auth의 `401 WWW-Authenticate` 응답이 브라우저까지 보존되지 않는 현상을 실제로 확인했기 때문에, 일반적인 `302` 로그인 화면과 `HttpOnly` 세션 쿠키 방식으로 전환했다.

## 사전 조건

프론트엔드와 백엔드가 각각 다음 주소에서 실행 중이어야 한다.

- `http://127.0.0.1:5173`
- `http://127.0.0.1:8080/actuator/health`

macOS 도구 설치:

```bash
brew install cloudflared
```

`cloudflared`는 Homebrew 서비스로 등록하지 않는다. 인증 프록시와 터널 모두 스크립트가 선택한 시간 동안만 일반 사용자 프로세스로 실행한다.

## 사용법

선택 메뉴:

```bash
./scripts/remote-access.sh
```

명령으로 직접 선택:

```bash
# 외부 공개 시작
./scripts/remote-access.sh quick-tunnel

# 상태 확인
./scripts/remote-access.sh status

# 인증 정보 다시 확인
./scripts/remote-access.sh credentials

# 외부 공개 종료
./scripts/remote-access.sh stop

# 로컬 전용 모드로 전환
./scripts/remote-access.sh local
```

`quick-tunnel`을 실행한 터미널은 연결을 유지하기 위해 열린 상태로 남는다. 해당 터미널에서 `Ctrl+C`를 누르거나 다른 터미널에서 `stop` 또는 `local`을 실행하면 인증 프록시와 공개 터널이 함께 종료된다. 터미널이 닫히거나 프로세스가 비정상 종료되어도 스크립트의 종료 처리에서 공개 연결을 정리한다.

터널은 잊고 켜두는 상황을 줄이기 위해 기본 8시간 후 자동 종료된다. 더 짧은 시간이 필요하면 초 단위로 지정할 수 있다.

```bash
# 1시간 뒤 자동 종료
AUKNOWLOG_TUNNEL_MAX_SECONDS=3600 ./scripts/remote-access.sh quick-tunnel
```

기본 사용자명은 `eunjuny`다. `quick-tunnel`은 실행할 때마다 32자리 임시 비밀번호와 64자리 세션 토큰을 새로 만든다. 인증 성공 시 브라우저에는 `Secure`, `HttpOnly`, `SameSite=Strict` 속성의 세션 쿠키가 저장된다. 평문 비밀번호, 프로세스 ID와 로그는 `.runtime/remote-access`에 권한 `600/700`으로 저장되고 Git에서 제외된다. 터널을 종료하면 인증 정보와 공개 URL을 삭제하며 이전 세션 토큰도 무효가 된다.

고정 사용자명 또는 비밀번호가 필요한 경우 실행 프로세스에만 환경 변수로 전달할 수 있다.

```bash
AUKNOWLOG_TUNNEL_USERNAME=my-name \
AUKNOWLOG_TUNNEL_PASSWORD='20자-이상의-임시-비밀번호' \
./scripts/remote-access.sh quick-tunnel
```

셸 히스토리에 비밀번호가 남을 수 있으므로 평소에는 자동 생성값 사용을 권장한다.

## 자동 안전 검증

스크립트는 공개 URL을 출력하기 전에 다음을 확인한다.

1. 프론트엔드와 백엔드 Health API가 응답하는지 확인
2. 인증 프록시 테스트(`node scripts/tunnel-auth-proxy.test.mjs`)와 실행 상태 확인
3. 로컬 인증 프록시에서 미인증 요청이 로그인 화면으로 전환되는 `302`인지 확인
4. 유효한 세션 요청이 `200`인지 확인
5. Quick Tunnel URL 생성 확인
6. 공개 URL에서도 미인증 `302`, 인증 성공 `200` 확인

어느 단계든 실패하면 시작한 인증 프록시와 cloudflared 프로세스를 정리한다.

## 한계와 다음 단계

- Quick Tunnel URL은 실행할 때마다 바뀌며 개발·테스트 용도다.
- 맥북, Docker, 백엔드와 프론트엔드가 실행 중이어야 한다.
- 임시 인증 프록시는 단일 사용자용이며 사용자별 권한과 계정 복구 기능은 없다.
- 외부 공개 중에는 OpenAI 생성 API도 인증 뒤에서 접근 가능하므로 API 사용 한도와 애플리케이션 레벨 요청 제한이 별도로 필요하다.
- 개인 상시 접속은 Tailscale, 공개 포트폴리오는 정식 배포와 애플리케이션 인증을 사용한다.
