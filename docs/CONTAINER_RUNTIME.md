# 전체 애플리케이션 컨테이너 실행

## 왜 추가했는가

기존에는 PostgreSQL·Prometheus·Keycloak만 컨테이너로 실행하고 Spring Boot와 Vite는 호스트 프로세스로 실행했습니다. 개발할 때는 빠르지만 새 환경에서 Java/Node 설치와 프록시 설정을 손으로 맞춰야 했습니다. 선택형 `docker-compose.app.yml`을 더해 같은 애플리케이션 이미지를 로컬과 CI에서 기동·검증할 수 있게 했습니다. 기존 호스트 개발 흐름은 유지합니다.

## 기본 구성과 비용 경계

```text
브라우저 -> 127.0.0.1:5173 -> Nginx 정적 Vue 화면
                                  -> /api/ -> Spring Boot:8080
                                               -> PostgreSQL + pgvector:5432
```

- `backend/Dockerfile`: Java 21 멀티스테이지 빌드. 런타임은 JRE·비루트 사용자로 실행합니다.
- `frontend/Dockerfile`: Node 22에서 정적 번들을 빌드하고 Nginx로 제공합니다. 개발 서버와 브라우저 개발 도구를 런타임에 넣지 않습니다.
- Nginx는 `/api/`만 백엔드로 전달하고 로컬 전용 원격 접속 메일 API는 404로 차단합니다. 백엔드 호스트 포트도 루프백에만 엽니다.
- 기본 Compose 모드는 기존 로컬 단일 사용자입니다. 자동 데일리 AI 생성·학습 메일·임베딩을 꺼 두고 OpenAI 키도 전달하지 않으므로 자동 외부 호출이 없습니다. 호스트의 기존 `OPENAI_API_KEY`도 자동 승계하지 않습니다. 실제 AI 학습을 원할 때만 `AUKNOWLOG_CONTAINER_OPENAI_API_KEY`를 별도로 전달하고, 자동 생성·임베딩은 각각 `AUKNOWLOG_CONTAINER_DAILY_LEARNING_ENABLED`, `AUKNOWLOG_CONTAINER_EMBEDDINGS_ENABLED`로 명시적으로 켭니다. 학습 메일도 별도 `AUKNOWLOG_CONTAINER_LEARNING_MAIL_ENABLED`와 컨테이너 전용 SMTP 설정이 필요합니다. 비밀값은 이미지·Compose 파일·Git에 넣지 않습니다.
- 기존 `backend/application-api.properties`는 이미지에 복사되지 않습니다. 컨테이너 모드에서는 환경 변수 또는 별도 시크릿 주입이 필요합니다. 호스트 실행용 개인 설정을 자동으로 컨테이너에 전달하지 않습니다.
- Notion 키 없이도 백엔드를 시작할 수 있습니다. 실제 Notion 내보내기를 선택할 때만 키가 필요하며, 미설정이면 명확한 오류를 반환합니다.
- 현재 앱 Compose는 인증을 끈 로컬 모드를 검증합니다. Keycloak 로그인용 프론트 빌드와 OIDC issuer/JWKS 주소는 배포 주소가 고정된 별도 환경 구성이 필요하며, 현재 Compose 검증 범위에 포함하지 않습니다. 따라서 컨테이너 기동만으로 다중 사용자 운영 준비가 끝났다고 주장하지 않습니다.

## 실행

호스트의 5173·8080·5432 포트가 비어 있다면 저장소 루트에서 다음을 실행합니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml up --build -d --wait
bash scripts/verify-compose-app.sh
docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

`down`은 DB 볼륨을 유지합니다. 볼륨 제거(`down -v`)는 테스트처럼 폐기 가능한 데이터에만 사용합니다. 기존 호스트 개발 서버가 실행 중이면 중지하거나 `FRONTEND_PORT`, `BACKEND_PORT`, `POSTGRES_PORT`를 다른 값으로 지정합니다.

기존 DB/포트와 분리된 일회성 검증은 다음과 같습니다.

```bash
COMPOSE_PROJECT_NAME=auknowlog-smoke POSTGRES_PORT=15433 BACKEND_PORT=18080 FRONTEND_PORT=15173 \
  docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.smoke.yml up --build -d --wait
BACKEND_PORT=18080 FRONTEND_PORT=15173 bash scripts/verify-compose-app.sh
COMPOSE_PROJECT_NAME=auknowlog-smoke POSTGRES_PORT=15433 BACKEND_PORT=18080 FRONTEND_PORT=15173 \
  docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.smoke.yml down -v
```

마지막 `down -v`는 이름이 분리된 `auknowlog-smoke` 검증 볼륨에만 적용합니다. 실제 학습 DB에 사용하면 안 됩니다.

## 검증 범위

`verify-compose-app.sh`는 백엔드 Health 200, 정적 화면 200, Nginx를 통한 대시보드 API 200, 원격 접속 메일 API의 프론트 경로 404를 확인합니다. GitHub Actions `Container smoke verification`도 이미지 빌드·Compose 기동·같은 HTTP 계약을 검증하고 임시 CI 볼륨을 정리합니다. 테스트는 실제 OpenAI·SMTP 호출을 하지 않습니다. 기존 Java 단위/실제 DB 통합 테스트와 Vue 단위/브라우저 E2E는 별도 워크플로에서 계속 실행합니다.

2026-09-30 로컬 검증에서는 `auknowlog-smoke`라는 별도 프로젝트·DB 볼륨·포트로 이미지 빌드와 세 서비스 Health를 통과했고, 네 가지 HTTP 검증도 통과했습니다. 해당 검증 볼륨과 컨테이너만 제거했으며 기존 개발 DB는 유지했습니다. 백엔드 `check --rerun-tasks`, Vue 단위 5개·Playwright 3개·프로덕션 빌드도 성공했습니다. 원격 GitHub Actions 실행 결과는 아직 확인하지 않았습니다.

운영 환경에서는 HTTPS 종단, 고정 OIDC redirect/issuer, 시크릿 관리, 이미지 취약점 검사·버전 고정, 백업·복원과 배포/롤백 절차가 추가로 필요합니다. 이 Compose 검증은 운영 배포 완료를 의미하지 않습니다.
