# auknowlog

AI를 활용하여 원하는 주제에 대한 객관식 문제를 자동으로 생성하고, 학습 기록을 관리해주는 애플리케이션입니다.

## 📖 프로젝트 소개

`auknowlog`는 **auto(자동)** + **knowledge(지식)** + **log(기록)**의 합성어입니다.

사용자가 학습 자료와 주제를 입력하면, OpenAI GPT가 자료 근거를 함께 남길 수 있는 객관식 퀴즈를 생성합니다. 풀이 결과와 오답 복습 일정을 저장하며, 정확 해시와 선택적 의미 유사도 검사로 중복 문제를 줄입니다.

## 🛠️ 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.5, Virtual Threads |
| **Frontend** | Vue.js 3, Vite |
| **AI** | OpenAI GPT-5.4 mini (`Responses API`, Structured Outputs) |
| **Database** | PostgreSQL 16 (이력 저장) |
| **Semantic Search** | PostgreSQL 16 + pgvector 0.8 (선택적 임베딩 유사도 검사) |
| **Observability** | Spring Boot Actuator, Micrometer, OpenTelemetry (선택적 Langfuse 연동) |
| **Notification** | Spring Mail, Gmail SMTP (명시적으로 실행하는 개인 원격 접속 안내) |
| **Infra** | Docker Compose |

## ✨ 주요 기능

- 🤖 **AI 퀴즈 자동 생성** - 주제 입력만으로 객관식 퀴즈 생성
- 📚 **학습 자료 기반 생성** - 파일·공개 URL의 본문을 청크로 저장하고 제한된 문맥으로 AI 학습 로드맵 생성
- 🔐 **안전한 자료 가져오기** - TXT·Markdown·PDF 파일과 공개 URL을 SSRF·크기·시간 제한 아래 추출하고 미리보기 후 저장
- 🧠 **학습 기록과 복습 예약** - 풀이 결과를 저장하고 오답은 다음 날 복습 대상으로 예약
- 🔍 **의미 기반 중복 방지** - pgvector 코사인 유사도와 PostgreSQL 정확 해시를 조합
- 📊 **AI 운영 관측** - 호출 모델·지연·성공/실패·토큰 사용량을 기록
- 🔭 **AI Trace 분석** - 선택 시 Langfuse에 생성·임베딩·중복 검사의 요청 흐름을 OpenTelemetry로 기록
- 🧭 **핵심 내용 기반 학습 로드맵** - AI가 대주제·소주제별 필수 목표와 문제 수를 설계하고, 아직 다루지 않은 목표를 우선 출제하며 목표 달성 뒤 추가 학습 또는 다음 단계 진행을 선택
- 🗂️ **직관적인 로드맵 관리** - 진행 중/완료 목록을 탭으로 분리하고, 계획은 삭제해도 퀴즈·풀이 기록은 보존
- 🗂️ **로드맵별 Git 학습 노트** - 채점 후 저장한 Markdown을 로드맵·단계별 디렉터리로 정리하고 `notes` 원격에 누적
- 💬 **문제 품질 피드백** - 제출한 문항의 정확성·난이도·해설 품질 의견을 저장하고 대시보드에서 집계
- 🧪 **AI 품질 평가** - 실제 문제 쌍의 pgvector 임계값과 로드맵 목표·문항 일치도를 평가하고 애매한 사례만 사람 검토
- 🧫 **중복 평가 기준 데이터셋** - 학습 이력과 분리한 42개 백엔드 문제 쌍의 참조 라벨·실제 벡터 유사도로 임계값을 재현 가능하게 검증

## 🚀 빠른 시작

### 1. 인프라 실행

```bash
# PostgreSQL + pgvector 시작
docker-compose up -d
```

로컬 PostgreSQL이 이미 `5432` 포트를 사용 중이면 다음처럼 프로젝트 DB를 `5433`으로 실행하고, 백엔드 실행 시 같은 포트를 지정합니다.

```bash
POSTGRES_PORT=5433 docker-compose up -d
```

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

```bash
# Docker DB를 5433으로 실행한 경우
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/auknowlog ./gradlew bootRun
```

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

### 4. 접속

| 서비스 | URL |
|--------|-----|
| 프론트엔드 | http://localhost:5173 |
| Swagger API | http://localhost:8080/swagger-ui.html |

### 5. 접속 모드 선택

기본값은 외부 공개가 없는 `local`이다. 다른 네트워크에서 임시로 확인할 때만 인증이 적용된 Cloudflare Quick Tunnel을 선택할 수 있다.

```bash
# 대화형 메뉴에서 local / quick-tunnel 선택
./scripts/remote-access.sh

# 또는 직접 실행
./scripts/remote-access.sh quick-tunnel
# 새 Quick Tunnel을 만들고 인증 검증 후, 설정된 본인 메일로 URL·임시 인증 정보를 1회 전송
./scripts/remote-access.sh quick-email
./scripts/remote-access.sh status
./scripts/remote-access.sh stop
```

Quick Tunnel에는 로그인·세션 인증 프록시가 선행되며 미인증 요청은 애플리케이션에 도달하지 않는다. `quick-email`은 설정된 단일 수신 주소로만 보낼 수 있고, 외부 브라우저에서는 호출할 수 없다. 설치와 보안 설계, 종료 방법은 [외부 접속 모드 운영 가이드](docs/REMOTE_ACCESS.md)를 참고한다.

Codex 로컬 자동화 `Auknowlog 원격 접속 메일`은 매일 오전 8시(로컬 시간)에 필요한 서비스를 확인·기동하고, 기존 임시 터널을 새 터널로 교체한 뒤 같은 메일 흐름을 실행한다. 맥과 Codex 호스트가 켜져 있고 네트워크에 연결돼 있어야 한다.

## 📚 문서

자세한 설명은 [개발 가이드](docs/DEVELOPMENT_GUIDE.md)를 참고하세요.

- [개선 로드맵](docs/IMPROVEMENT_ROADMAP.md)
- [통합 설계·운영 가이드](docs/PROJECT_GUIDE.md) - 기능, 전체 흐름, 데이터 모델, OSS 선택 근거와 검증 기준
- [pgvector·Testcontainers 통합 테스트](docs/PGVECTOR_INTEGRATION_TEST.md)
- [pgvector·Testcontainers 3회차 학습 문서 (HTML)](docs/PGVECTOR_STUDY_CURRICULUM.html)
- [퀴즈 생성·중복 필터링 전체 흐름 (HTML)](docs/QUIZ_GENERATION_DUPLICATE_FILTER_FLOW.html)
- [외부 접속 모드 운영 가이드](docs/REMOTE_ACCESS.md)
- [외부 접속 구조와 동작 흐름 (HTML)](docs/REMOTE_ACCESS_FLOW.html)
- [포트폴리오 완성도·부족한 부분 점검](docs/PORTFOLIO_GAP_ANALYSIS.md)
- [Langfuse 기반 AI 관측·비용 없는 설정](docs/LANGFUSE_OBSERVABILITY.md)
- [Prometheus 기반 AI·문제 품질 운영 관측](docs/PROMETHEUS_OBSERVABILITY.md)
- [AI 품질 평가와 Human-in-the-loop](docs/AI_QUALITY_EVALUATION.md)
- [파일·URL 학습 자료 수집 보안 설계](docs/SOURCE_INGESTION_SECURITY.md)
- [`.roadmap.json` 단계형 학습 로드맵 형식](docs/ROADMAP_JSON_FORMAT.md)
- [로드맵 진행 선택·Git 학습 노트 구조](docs/ROADMAP_PROGRESS_AND_GIT_EXPORT.md)

- API 명세
- 데이터베이스 스키마
- pgvector 기반 의미 중복 검사
- 트러블슈팅

이전 구조와 새 구조의 문제·대안·선택 근거·검증 방법은 [스택 전환 비교](docs/STACK_TRANSITION.md)에 정리했습니다. 다음 도입 기준은 [기술 의사결정 기록](docs/TECHNOLOGY_DECISIONS.md)을 참고하세요.

## 🔎 운영 확인

- 스키마는 Flyway 마이그레이션으로 관리하며 애플리케이션 시작 시 검증합니다.
- AI 호출 지연·결과·토큰 사용량은 `/actuator/metrics`에서 진단하고 `/actuator/prometheus`로 수집합니다. 예: `/actuator/metrics/auknowlog.ai.request.duration`
- Prometheus는 기본 실행에서 제외되며 `docker compose --profile monitoring up -d`로 필요할 때만 실행합니다. UI는 `http://127.0.0.1:9090`입니다.
- 백엔드와 Prometheus를 실행한 뒤 `./scripts/verify-prometheus.sh`를 실행하면 프로젝트 전용 지표 노출, 고카디널리티 label 부재, 실제 scrape target의 `UP` 상태를 한 번에 확인합니다. 이 검증은 OpenAI API를 호출하지 않습니다.
- 화면은 기본적으로 비용 없는 더미 퀴즈 모드입니다. 실제 GPT 생성은 화면에서 해제하고 `OPENAI_API_KEY`를 설정한 경우에만 실행됩니다.
- 의미 중복 검사용 임베딩은 기본 활성입니다. 실제 AI 퀴즈 생성 시 Embeddings API가 함께 호출되며, 비활성화하려면 `AUKNOWLOG_EMBEDDINGS_ENABLED=false`를 설정하세요.
- Langfuse 관측은 기본 비활성입니다. 별도 비용 없이 Cloud Hobby를 연결하는 방법과 개인정보 기본값은 [Langfuse 운영 문서](docs/LANGFUSE_OBSERVABILITY.md)를 참고하세요.
- `품질 평가`의 중복 후보 수집·검토·지표 조회는 저장된 DB 데이터만 사용합니다. 기준 데이터셋의 문장 저장도 비용이 없지만, `실제 유사도 계산`은 사용자가 확인한 경우에만 OpenAI Embeddings API를 호출합니다. 목표·문항 AI 평가는 사용자가 버튼을 누르고 확인한 경우에만 선택한 학습 단위 하나를 OpenAI로 평가합니다.

## ✅ 테스트

```bash
cd backend

# 빠른 단위·H2 테스트
./gradlew test

# Docker의 실제 PostgreSQL 16 + pgvector 통합 테스트
./gradlew integrationTest

# 위 두 종류를 모두 실행(CI와 동일)
./gradlew check

# 실행 중인 백엔드와 Prometheus의 실제 수집 상태 확인
cd ..
./scripts/verify-prometheus.sh
```

`integrationTest`는 Testcontainers가 격리된 임시 DB를 만들고 Flyway V1~V16, `vector(512)`, HNSW 인덱스, 코사인 유사도 검색, 평가 문제 쌍·분리 기준 데이터셋 벡터 저장, 511차원 벡터 거부와 피드백·계층형 로드맵·자료 출처·학습 목표 및 문항 연결 스키마를 검증한 뒤 컨테이너를 제거합니다. OpenAI API는 호출하지 않습니다.

## ⚙️ 환경 설정

환경 변수로 API 키를 설정합니다:

```bash
export OPENAI_API_KEY="your_api_key"
```

또는 Git에 포함되지 않는 `backend/application-api.properties`에 아래와 같이 설정할 수 있습니다.

```properties
auknowlog.openai.api.key=your_api_key
auknowlog.openai.embedding.enabled=true

# quick-email을 사용할 때만 필요 (실제 값은 Git에 넣지 않음)
auknowlog.mail.username=sender@example.com
auknowlog.mail.app-password=generated-gmail-app-password
auknowlog.mail.recipient=receiver@example.com
```

## 📁 프로젝트 구조

```
auknowlog/
├── backend/                 # Spring Boot 백엔드
│   └── src/main/java/com/auknowlog/backend/
│       ├── quiz/           # 퀴즈 생성 (Controller, Service)
│       ├── learning/       # 퀴즈·풀이·오답 복습 도메인
│       ├── source/         # 학습 자료·청크 관리
│       ├── embedding/      # pgvector 의미 중복 검사
│       └── document/       # 레거시 Markdown·외부 저장 연동
├── frontend/               # Vue.js 프론트엔드
├── docs/                   # 문서
└── docker-compose.yml      # 인프라 설정
```
