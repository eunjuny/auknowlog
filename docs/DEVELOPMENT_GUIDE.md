# Auknowlog 개발 가이드

## 서비스 흐름

```text
학습 자료 저장 → 청크 생성 → 퀴즈 생성 → 풀이 제출 → 오답 복습 예약
                      ↘ 선택 시 OpenAI 임베딩 → pgvector 의미 중복 검사
```

기본 화면은 비용 없는 더미 퀴즈를 생성한다. AI 모델과 임베딩 호출은 사용자가 설정과 화면에서 명시적으로 활성화할 때만 발생한다.

## 기술 구성

| 구분 | 구성 | 역할 |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3.5, Spring MVC, JPA | API, 트랜잭션, 입력 검증 |
| Database | PostgreSQL 16 + pgvector | 학습 이력의 원본 데이터와 선택적 의미 검색 |
| Migration | Flyway | 리뷰 가능한 DB 스키마 버전 관리 |
| AI | OpenAI Responses API, Embeddings API | 구조화 퀴즈 생성, 선택적 의미 중복 검사 |
| Observability | Actuator, Micrometer, Prometheus | AI 지연·결과·토큰 및 문제 품질 추세 |
| Frontend | Vue 3, Vite | 자료 입력, 풀이, 결과 저장 |
| Local Infra | Docker Compose | 개발 환경 재현 |

전환의 상세 근거는 [스택 전환 비교](STACK_TRANSITION.md)를 참고한다.

## 실행

```bash
docker-compose up -d

cd backend
./gradlew bootRun

cd ../frontend
npm install
npm run dev
```

| 서비스 | 주소 |
| --- | --- |
| 프런트엔드 | http://localhost:5173 |
| API/Swagger | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| AI 지연 메트릭 | http://localhost:8080/actuator/metrics/auknowlog.ai.request.duration |
| Prometheus 형식 | http://localhost:8080/actuator/prometheus |
| Prometheus UI (`monitoring` 프로필) | http://127.0.0.1:9090 |

Prometheus는 기본 실행에 포함되지 않는다. 백엔드를 `8080` 포트로 실행한 뒤 필요할 때만 다음 명령으로 시작한다.

```bash
docker compose --profile monitoring up -d prometheus
```

커스텀 지표, PromQL 예시와 데이터 책임 범위는 [Prometheus 운영 관측](PROMETHEUS_OBSERVABILITY.md)을 참고한다.

외부 접속은 기본적으로 비활성화된다. 필요할 때만 다음 선택 메뉴에서 `quick-tunnel`을 고른다.

```bash
./scripts/remote-access.sh
```

구성과 보안 경계는 [외부 접속 모드 운영 가이드](REMOTE_ACCESS.md)를 참고한다.

## 비용이 발생하는 설정

OpenAI API 키를 설정하고 화면의 데모 모드를 해제하면 실제 API를 호출한다. 의미 중복 검사는 기본 활성이고, 필요하면 환경 변수로 끌 수 있다.

```bash
export OPENAI_API_KEY="your-api-key"

# 의미 중복 검사를 끄고 SHA-256 검사만 사용하려는 경우
export AUKNOWLOG_EMBEDDINGS_ENABLED=false
```

- 화면의 `비용 없는 데모 퀴즈로 생성`을 해제하면 Responses API 호출이 발생한다.
- 기본 설정에서는 새 문제 후보마다 Embeddings API 호출이 발생할 수 있다.
- API 오류·한도 초과 시 무료 모델로 자동 전환하지 않는다.

## API

| Method | Endpoint | 설명 | 외부 비용 |
| --- | --- | --- | --- |
| POST | `/api/sources` | 학습 자료와 청크 저장 | 없음 |
| POST | `/api/quizzes/dummy` | 비용 없는 더미 퀴즈와 학습 퀴즈 저장 | 없음 |
| POST | `/api/quizzes/create` | OpenAI로 퀴즈 생성 후 저장 | 발생 가능 |
| POST | `/api/learning-attempts` | 풀이 기록 저장, 오답 복습 예약 | 없음 |

### 학습 자료 저장

```json
POST /api/sources
{
  "title": "JVM 실행 구조",
  "content": "JVM은 Java 바이트코드를 실행합니다. JIT 컴파일러는 ..."
}
```

### 비용 없는 샘플 퀴즈와 풀이 저장

```json
POST /api/quizzes/dummy
{
  "topic": "Java",
  "numberOfQuestions": 2,
  "sourceId": 1
}
```

```json
POST /api/learning-attempts
{
  "quizId": 1,
  "answers": [
    { "questionOrder": 1, "selectedAnswer": "선택지 B" },
    { "questionOrder": 2, "selectedAnswer": "선택지 A" }
  ]
}
```

오답 문항은 다음 날 `review_schedule`에 `PENDING` 상태로 저장된다.

## 테스트

```bash
cd backend
./gradlew test
./gradlew integrationTest
# 단위 테스트와 Docker 통합 테스트를 모두 실행
./gradlew check
```

- Responses API 요청·응답 및 사용량은 MockRestServiceServer로 검증한다.
- H2 통합 테스트는 자료 저장 → 더미 퀴즈 → 풀이 → 복습 예약을 실제 HTTP와 JPA로 검증한다.
- Testcontainers 통합 테스트는 실제 PostgreSQL 16 + pgvector에서 V3 마이그레이션, HNSW 인덱스와 코사인 검색을 검증한다.
- 현재 자동 테스트는 라이브 OpenAI API를 호출하지 않는다.
