# osc 추가 구현

이 문서는 오픈소스컨설팅 지원을 위해 진행한 Keycloak 인증·권한·사용자별 학습 분리 작업의 지속 기록입니다. 이후 대화에서 **“osc 추가 구현”**은 이 범위를 가리킵니다. 앱 전체에 자동으로 공유되는 메모리 대신 저장소에 남겨 다음 작업에서도 참고할 수 있도록 합니다.

## 현재 범위

2차 작업(관리자 감사·사용자별 AI 예산/호출 제한·본인 이메일 인증과 학습 알림)은 [사용자 운영 정책](ACCOUNT_OPERATIONS.md)에 동작·제약·실행·검증을 정리합니다. V22를 사용하며 새 Redis/브로커나 유료 외부 서비스를 필수로 추가하지 않습니다.

- Keycloak OIDC Authorization Code + PKCE 로그인, Spring Security Bearer JWT 검증
- USER 개인 학습 기능, ADMIN 품질 평가·전체 사용자 학습/AI 모니터링 권한
- 자료·로드맵·퀴즈 소유권, 하위 풀이·복습·피드백 분리
- 공용 데일리 콘텐츠와 사용자별 퀴즈·완료 상태 분리
- Flyway V20 소유권·V21 AI 사용자 귀속, 401/403/200 권한 검증과 사용자별 자료 격리 검증

상세 구조·실행 방법은 [인증·사용자 데이터 분리](AUTHENTICATION_AND_TENANCY.md)를 참고합니다.

## 샘플 사용자

| 사용자 | 역할 | 샘플 학습 |
| --- | --- | --- |
| test1 | USER | Kubernetes Pod 로드맵, 오답 풀이 기록, 오늘 복습 |
| test2 | USER | PostgreSQL 트랜잭션 로드맵, 정답 풀이 기록, 수동 등록 형태의 오늘 복습 |
| app-admin | USER + ADMIN | 전체 관리자 화면, 사용자별 기록·풀이 문항 상세, 전체 AI 모니터링 |

`scripts/seed-osc-samples.sql`은 개발 DB에 위 데이터를 추가합니다. 샘플 퀴즈 제목의 `[OSC SAMPLE]` 표식으로 재실행 시 중복을 막고 기존 학습 데이터는 변경하지 않습니다. AI 문제 이력이나 평가 데이터셋에는 넣지 않아 실제 품질 측정 표본과 혼동하지 않습니다.

```bash
docker exec -i auknowlog-postgres psql -U auknowlog -d auknowlog -v ON_ERROR_STOP=1 < scripts/seed-osc-samples.sql
```

로그인 계정은 Keycloak에 같은 사용자명으로 만들고 USER realm role을 부여합니다. 최초 로그인 시 `local-sample-*` 소유자를 검증된 JWT sub에 연결하므로 샘플을 이어서 볼 수 있습니다. 비밀번호는 Git에서 제외한 로컬 런타임 파일에만 저장합니다.

```bash
node scripts/setup-osc-sample-accounts.mjs
```

이 명령은 Keycloak을 기동하고 실제 test1·test2 로그인 계정을 만듭니다. 관리자·사용자 비밀번호는 무작위로 생성해 `.runtime/osc-sample-accounts.json`에 권한 600으로 보관합니다. 계정은 개발 샘플이며 공용 배포용으로 사용하지 않습니다. 프론트 `VITE_AUTH_ENABLED=true`, 백엔드 `SPRING_PROFILES_ACTIVE=keycloak`으로 실행하면 계정별 샘플을 확인할 수 있습니다.

## 마스터 계정의 의미

Keycloak의 **master realm 관리자**는 로그인 시스템·realm·사용자를 관리하는 인프라 계정입니다. Auknowlog의 **ADMIN**은 품질 평가와 전체 사용자 학습/AI 모니터링을 담당합니다. 관리자도 일반 학습 API에서는 자신의 데이터만 조회하며 타인 기록 조회는 별도의 `/api/admin/**` 읽기 전용 경계에서만 허용합니다.

샘플 준비 명령은 master realm bootstrap 관리자와 앱용 `app-admin`을 만듭니다. 비공개 런타임 파일의 `administrator`는 로그인 시스템 관리자, `applicationAdmin`은 앱 전체 관리자입니다. 앱 화면에서는 `applicationAdmin`으로 로그인합니다. 기존 비밀번호는 재설정하지 않습니다.

관리자 열람 감사와 사용자별 AI 한도 변경은 2차 범위로 구현했습니다. 조직/고객사 tenant, 감사 보존/변조 방지, GitHub 사용자별 내보내기와 포트폴리오 PDF/PPTX 갱신은 후속 범위입니다. 관리자는 타인 학습 기록을 수정·삭제하거나 Keycloak 역할을 변경할 수 없습니다.

## 전체 관리자 모니터링

`app-admin`으로 로그인하면 상단 **전체 관리자** 메뉴가 나타납니다.

- 전체 사용자·풀이·로드맵·복습 대기 수
- 사용자별 풀이/로드맵 수와 측정 AI 토큰, 사용자 선택 필터
- 페이지 단위 전체/사용자별 풀이·로드맵·복습 일정/재풀이·데일리 진행·AI 호출 원장
- 풀이 상세의 문항·모든 보기·선택 답·정답·보기별 해설
- 전체 AI 호출/실패/토큰/평균 지연, 최근 14일 사용량, 당일 공유 토큰 안전 예산

`GET /api/admin/summary`, `/users`, `/records/{attempts|roadmaps|reviews|review-attempts|daily|ai}`, `/attempts/{id}`를 사용합니다. 목록은 기본 20건, 최대 100건입니다. `userId`는 관리자 API에서만 필터로 사용하며 일반 사용자 API 소유권을 바꾸지 않습니다. SQL은 고정 종류와 바인딩 인자를 사용합니다. JWT 미인증은 401, USER는 403이며 인증을 끈 모드에서도 관리자 API는 차단합니다. 이메일·Keycloak subject·토큰·프롬프트·시크릿은 관리자 응답에 포함하지 않습니다.

V21의 `ai_generation_log.owner_id`는 검증된 현재 사용자를 기록합니다. 백그라운드 자동 생성과 이전 로그는 NULL로 유지하고 **시스템 / 기존 미귀속**으로 표시합니다. 생성(퀴즈·로드맵·품질 평가·데일리)뿐 아니라 임베딩 API의 측정 토큰·실패·지연도 앞으로 기록합니다. 미측정 토큰은 원장에서 `미측정`으로 표시하며 합계에는 더하지 않습니다. 토큰 원장은 OpenAI 청구서나 모든 외부 서비스의 비용을 대신하지 않습니다. 일반 USER 대시보드 최근 AI 호출 지표는 본인 원장만 집계하고 일일 안전 예산은 프로젝트 공용 정책입니다.

관리자 열람은 개인정보 접근 권한입니다. 운영 배포 전에는 공개 샘플 계정을 사용하지 않고 최소 권한·감사 보존 정책·고정 HTTPS 도메인을 적용해야 합니다. 유료 서비스 가입을 추가하지 않으며 조회·검증은 AI를 호출하지 않습니다.

## 실제 확인 결과 (2026-09-29)

- Keycloak 컨테이너 기동 및 realm OIDC discovery 200
- test1·test2 실제 Chromium 로그인 → Axios Bearer JWT → 사용자 자동 연결 성공
- 각 계정은 자기 주제의 풀이·로드맵·복습만 조회
- 다른 계정의 풀이 ID를 요청하면 양방향 404, USER의 품질 관리 API 요청은 403
- 미인증 풀이 조회는 401
- app-admin 실제 로그인·관리자 메뉴·사용자 필터·두 계정 풀이 상세·전체 AI 원장/요약 200, USER 관리자 API 403, 미인증 관리자 API 401
- 모바일 390px 화면에서 페이지 전체 가로 넘침 없음 (표는 내부 가로 스크롤)
- 백엔드 전체 테스트·Testcontainers PostgreSQL 통합 테스트 통과 (V21 적용 포함), Vue 단위 테스트·프로덕션 빌드 통과
- 임베딩 성공/실패는 모의 HTTP 응답으로 원장 기록을 검증하고 실 API는 호출하지 않음
- 샘플 SQL 재실행 후 사용자당 샘플 퀴즈·로드맵은 각 1건 유지
- `git diff --check`, 계정 준비·검증 스크립트 구문 확인 통과, OpenAI 호출 없음
- 2차 운영 검증: 백엔드 76개·실제 PostgreSQL 통합 15개·Vue 5개 테스트와 빌드 통과. 개인 설정/예산·ADMIN 한도 변경·감사 200/403, 8개 동시 예산 예약과 동시 알림 큐/worker, 모의 SMTP 재시도 확인. [세부 근거](ACCOUNT_OPERATIONS.md)

재검증 명령은 `node scripts/verify-osc-samples.mjs`입니다. 로컬 프론트·백엔드를 인증 모드로 실행하고 `npm ci` 및 `npx playwright install chromium`을 마친 환경에서 동작합니다. 테스트는 비밀번호와 JWT를 메모리에서만 사용하고 출력·스크린샷·trace에 남기지 않습니다.

2026-09-30에 실제 호스트 Keycloak 인증 모드에서 재실행해 test1·test2 로그인과 각자 기록, 교차 사용자 기록 404, ADMIN 전체 조회와 익명 요청 401을 다시 확인했습니다. 실제 OpenAI 호출·메일 발송은 하지 않았습니다. 선택형 전체 컨테이너 실행은 현재 로컬 단일 사용자 모드까지만 검증하며 Keycloak 인증 배포는 별도 구성 과제입니다.
