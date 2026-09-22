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

원격 접속 정보를 본인 메일로 받으려면 Git에서 제외되는 `backend/application-api.properties`에 아래 세 값도 넣는다. Gmail 계정은 2단계 인증 후 만든 **앱 비밀번호**를 사용하며 일반 Google 계정 비밀번호를 넣지 않는다.

```properties
auknowlog.mail.username=sender@example.com
auknowlog.mail.app-password=generated-gmail-app-password
auknowlog.mail.recipient=receiver@example.com
```

`username`은 Gmail SMTP 발신 계정, `recipient`는 고정된 본인 수신 주소다. 요청 본문으로 수신자를 받지 않으므로 Quick Tunnel 사용자가 임의 수신자에게 메일을 보낼 수 없다.

## 사용법

선택 메뉴:

```bash
./scripts/remote-access.sh
```

명령으로 직접 선택:

```bash
# 외부 공개 시작
./scripts/remote-access.sh quick-tunnel

# 새 터널을 열고 인증 동작을 검증한 뒤, URL·임시 인증 정보를 설정된 메일로 1회 전송
./scripts/remote-access.sh quick-email

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

`quick-email`도 같은 방식으로 터널을 유지한다. 다만 터미널에는 URL·비밀번호를 다시 출력하지 않고, SMTP 서버가 발송 요청을 수락한 뒤에만 일반 성공 메시지를 출력한다. 발송 실패 시 시작한 터널을 즉시 종료한다. SMTP 수락은 수신함 도착을 보장하지 않으므로, 실제 수신 여부는 설정된 메일함에서 확인한다.

## 모바일에서 데일리 학습 화면 열기

원격 접속 메일의 기본 URL로 로그인하면 앱이 서버의 오늘 데일리 학습 상태를 읽어, 미완료 시 데일리 학습을 첫 화면으로 보여주고 완료 시 대시보드를 보여준다. 별도 문서 경로나 정적 HTML은 외부에 제공하지 않는다. 터널이 종료되었거나 맥의 Vite·인증 프록시·cloudflared가 중단되면 이 기능도 사용할 수 없다.

## 매일 오전 8시 자동 실행

Codex 로컬 자동화 `Auknowlog 원격 접속 메일`이 매일 오전 8시(현재 호스트의 `Asia/Seoul`)에 다음 순서로 실행한다.

1. PostgreSQL·Spring Boot·Vite 상태를 확인하고 중단된 구성 요소를 문서화된 로컬 설정으로 기동한다.
2. 이전 Quick Tunnel이 남아 있으면 종료해 URL과 임시 비밀번호를 폐기한다.
3. 새 `quick-email` 흐름으로 외부 미인증 `302`·인증 성공 `200`을 검증한다.
4. 설정된 고정 수신 주소에 새 접속 정보와 만료 시각을 한 번 전송한다.
5. 성공한 터널은 기본 최대 8시간 유지하고, 실패한 실행에서 새로 만든 터널은 정리한다.

이 자동화는 클라우드 서버가 아니라 사용자의 로컬 Codex 작업에 연결되어 있다. 따라서 예약 시각에 맥과 Codex 호스트가 실행 중이고 인터넷·Docker를 사용할 수 있어야 한다. 절전·종료·네트워크 단절 상태에서는 메일을 보낼 수 없으며, 실패 시 비밀값을 제외한 조치 정보만 남긴다. 퀴즈 생성이나 임베딩 API는 호출하지 않으므로 OpenAI API 비용은 발생하지 않는다.

터널은 잊고 켜두는 상황을 줄이기 위해 기본 8시간 후 자동 종료된다. 더 짧은 시간이 필요하면 초 단위로 지정할 수 있다.

```bash
# 1시간 뒤 자동 종료
AUKNOWLOG_TUNNEL_MAX_SECONDS=3600 ./scripts/remote-access.sh quick-tunnel
```

기본 사용자명은 `eunjuny`다. `quick-tunnel`은 실행할 때마다 32자리 임시 비밀번호와 64자리 세션 토큰을 새로 만든다. 인증 정보 출력은 라벨과 값을 서로 다른 줄에 표시하므로 값 앞에 공백 없이 한 줄만 복사할 수 있다. 환경변수로 전달한 사용자명과 비밀번호도 시작·끝 공백을 제거한 뒤 사용한다. 인증 성공 시 브라우저에는 `Secure`, `HttpOnly`, `SameSite=Strict` 속성의 세션 쿠키가 저장된다. 평문 비밀번호, 프로세스 ID와 로그는 `.runtime/remote-access`에 권한 `600/700`으로 저장되고 Git에서 제외된다. 터널을 종료하면 인증 정보와 공개 URL을 삭제하며 이전 세션 토큰도 무효가 된다.

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
7. `quick-email`인 경우에만 URL·사용자명·임시 비밀번호·만료 시각을 localhost의 메일 API에 전달하고 SMTP 수락 확인

어느 단계든 실패하면 시작한 인증 프록시와 cloudflared 프로세스를 정리한다.

### 검증 결과 (2026-09-13)

- 인증 프록시 자체 테스트에서 미인증 `302`, 인증 성공 `200`, 프록시 헤더·쿠키 제거, 로그인 실패 횟수 제한을 확인했다.
- 실제 `quick-email` 실행에서 Quick Tunnel의 공개 URL 미인증 `302`·인증 성공 `200` 검증 뒤 Gmail SMTP의 발송 요청 수락을 확인했다.
- 터널 런타임 디렉터리와 인증 정보 파일 권한은 각각 `700`, `600`이며, 터널 프로세스가 실행 중인 상태를 확인했다. URL·수신 주소·비밀번호는 검증 로그와 문서에 기록하지 않았다.

## 메일 전송 경계

```text
remote-access.sh quick-email
  -> cloudflared URL·인증 프록시 검증
  -> .runtime/remote-access (권한 700/600)의 일회성 URL·인증 정보 읽기
  -> localhost:8080 /api/notifications/remote-access/email
  -> Spring Mail -> Gmail SMTP STARTTLS (587)
  -> 설정된 단일 수신 메일함
```

- 백엔드는 `127.0.0.1`에만 바인딩한다. 외부 요청은 인증 프록시와 Vite를 거쳐야 하며, Vite는 메일 발송 API를 프록시하지 않는다.
- 메일 API는 `trycloudflare.com` 루트 URL, 안전한 사용자명, 최소 20자 비밀번호와 미래 만료 시각만 받는다. 수신 주소·SMTP 계정·앱 비밀번호는 요청이나 응답에 포함하지 않는다.
- 스크립트는 비밀번호를 명령행 인수로 넘기지 않고 권한 `600`인 런타임 파일에서 읽는다. 성공·실패 로그에도 URL, 수신 주소, 비밀번호를 남기지 않는다.
- URL과 비밀번호가 같은 평문 메일에 포함되므로 이 기능은 개인 임시 접속 전용이다. 메일을 전달하지 말고, 사용 뒤 `stop`으로 터널을 끈다. 정식 서비스에서는 일회성 링크·계정 인증·감사 이력으로 교체한다.

## 한계와 다음 단계

- Quick Tunnel URL은 실행할 때마다 바뀌며 개발·테스트 용도다.
- 맥북, Docker, 백엔드와 프론트엔드가 실행 중이어야 한다.
- 임시 인증 프록시는 단일 사용자용이며 사용자별 권한과 계정 복구 기능은 없다.
- 외부 공개 중에는 OpenAI 생성 API도 인증 뒤에서 접근 가능하므로 API 사용 한도와 애플리케이션 레벨 요청 제한이 별도로 필요하다.
- 개인 상시 접속은 Tailscale, 공개 포트폴리오는 정식 배포와 애플리케이션 인증을 사용한다.
- 메일은 사용자가 `quick-email`을 실행하거나 등록된 오전 8시 Codex 자동화가 실행될 때만 전송된다. 장기 운영에서는 로컬 호스트 의존성을 제거한 정식 배포·스케줄러·비밀 저장소가 필요하다.
