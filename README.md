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
| **Infra** | Docker Compose |

## ✨ 주요 기능

- 🤖 **AI 퀴즈 자동 생성** - 주제 입력만으로 객관식 퀴즈 생성
- 📚 **학습 자료 기반 생성** - 파일·공개 URL의 본문을 청크로 저장하고 제한된 문맥으로 AI 학습 로드맵 생성
- 🔐 **안전한 자료 가져오기** - TXT·Markdown·PDF 파일과 공개 URL을 SSRF·크기·시간 제한 아래 추출하고 미리보기 후 저장
- 🧠 **학습 기록과 복습 예약** - 풀이 결과를 저장하고 오답은 다음 날 복습 대상으로 예약
- 🔍 **의미 기반 중복 방지** - pgvector 코사인 유사도와 PostgreSQL 정확 해시를 조합
- 📊 **AI 운영 관측** - 호출 모델·지연·성공/실패·토큰 사용량을 기록
- 🔭 **AI Trace 분석** - 선택 시 Langfuse에 생성·임베딩·중복 검사의 요청 흐름을 OpenTelemetry로 기록
- 🧭 **계층형 학습 로드맵** - 대주제·순차 소주제, 동시 진행, 자동 완료 분류, 편집 가능한 AI 미리보기, `*.roadmap.json` 가져오기 지원
- 💬 **문제 품질 피드백** - 제출한 문항의 정확성·난이도·해설 품질 의견을 저장하고 대시보드에서 집계

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
./scripts/remote-access.sh status
./scripts/remote-access.sh stop
```

Quick Tunnel에는 로그인·세션 인증 프록시가 선행되며 미인증 요청은 애플리케이션에 도달하지 않는다. 설치와 보안 설계, 종료 방법은 [외부 접속 모드 운영 가이드](docs/REMOTE_ACCESS.md)를 참고한다.

## 📚 문서

자세한 설명은 [개발 가이드](docs/DEVELOPMENT_GUIDE.md)를 참고하세요.

- [개선 로드맵](docs/IMPROVEMENT_ROADMAP.md)
- [pgvector·Testcontainers 통합 테스트](docs/PGVECTOR_INTEGRATION_TEST.md)
- [pgvector·Testcontainers 3회차 학습 문서 (HTML)](docs/PGVECTOR_STUDY_CURRICULUM.html)
- [퀴즈 생성·중복 필터링 전체 흐름 (HTML)](docs/QUIZ_GENERATION_DUPLICATE_FILTER_FLOW.html)
- [외부 접속 모드 운영 가이드](docs/REMOTE_ACCESS.md)
- [외부 접속 구조와 동작 흐름 (HTML)](docs/REMOTE_ACCESS_FLOW.html)
- [포트폴리오 완성도·부족한 부분 점검](docs/PORTFOLIO_GAP_ANALYSIS.md)
- [Langfuse 기반 AI 관측·비용 없는 설정](docs/LANGFUSE_OBSERVABILITY.md)
- [Prometheus 기반 AI·문제 품질 운영 관측](docs/PROMETHEUS_OBSERVABILITY.md)
- [파일·URL 학습 자료 수집 보안 설계](docs/SOURCE_INGESTION_SECURITY.md)
- [`.roadmap.json` 단계형 학습 로드맵 형식](docs/ROADMAP_JSON_FORMAT.md)

- API 명세
- 데이터베이스 스키마
- pgvector 기반 의미 중복 검사
- 트러블슈팅

이전 구조와 새 구조의 문제·대안·선택 근거·검증 방법은 [스택 전환 비교](docs/STACK_TRANSITION.md)에 정리했습니다. 다음 도입 기준은 [기술 의사결정 기록](docs/TECHNOLOGY_DECISIONS.md)을 참고하세요.

## 🔎 운영 확인

- 스키마는 Flyway 마이그레이션으로 관리하며 애플리케이션 시작 시 검증합니다.
- AI 호출 지연·결과·토큰 사용량은 `/actuator/metrics`에서 진단하고 `/actuator/prometheus`로 수집합니다. 예: `/actuator/metrics/auknowlog.ai.request.duration`
- Prometheus는 기본 실행에서 제외되며 `docker compose --profile monitoring up -d`로 필요할 때만 실행합니다. UI는 `http://127.0.0.1:9090`입니다.
- 화면은 기본적으로 비용 없는 더미 퀴즈 모드입니다. 실제 GPT 생성은 화면에서 해제하고 `OPENAI_API_KEY`를 설정한 경우에만 실행됩니다.
- 의미 중복 검사용 임베딩은 기본 활성입니다. 실제 AI 퀴즈 생성 시 Embeddings API가 함께 호출되며, 비활성화하려면 `AUKNOWLOG_EMBEDDINGS_ENABLED=false`를 설정하세요.
- Langfuse 관측은 기본 비활성입니다. 별도 비용 없이 Cloud Hobby를 연결하는 방법과 개인정보 기본값은 [Langfuse 운영 문서](docs/LANGFUSE_OBSERVABILITY.md)를 참고하세요.

## ✅ 테스트

```bash
cd backend

# 빠른 단위·H2 테스트
./gradlew test

# Docker의 실제 PostgreSQL 16 + pgvector 통합 테스트
./gradlew integrationTest

# 위 두 종류를 모두 실행(CI와 동일)
./gradlew check
```

`integrationTest`는 Testcontainers가 격리된 임시 DB를 만들고 Flyway V1~V12, `vector(512)`, HNSW 인덱스, 코사인 유사도 검색과 피드백·계층형 로드맵·자료 출처 및 로드맵 연결 스키마를 검증한 뒤 컨테이너를 제거합니다. OpenAI API는 호출하지 않습니다.

## ⚙️ 환경 설정

환경 변수로 API 키를 설정합니다:

```bash
export OPENAI_API_KEY="your_api_key"
```

또는 Git에 포함되지 않는 `backend/application-api.properties`에 아래와 같이 설정할 수 있습니다.

```properties
auknowlog.openai.api.key=your_api_key
auknowlog.openai.embedding.enabled=true
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
