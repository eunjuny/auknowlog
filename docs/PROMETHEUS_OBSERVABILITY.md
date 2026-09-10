# Prometheus 운영 관측

## 도입 목적

Auknowlog의 호출량은 많지 않으므로 트래픽 규모를 과장하기 위한 모니터링은 하지 않는다. Prometheus는 다음 운영 질문에 답하기 위한 선택형 관측 계층이다.

- OpenAI 퀴즈·로드맵 호출이 실패하거나 느려지고 있는가?
- 요청한 문제 중 정확 중복·의미 중복으로 제외되는 비율은 얼마인가?
- 중복 제거로 인해 모델 재호출과 토큰 사용이 늘어나는가?
- 임베딩을 사용할 수 없거나 인덱스 저장이 실패하는 일이 반복되는가?

정확한 요청 이력과 토큰 원장은 PostgreSQL의 `ai_generation_log`가 담당한다. Prometheus는 시간에 따른 변화와 이상 감지를 담당하며, 개별 주제·질문·요청 ID는 저장하지 않는다.

## 데이터 흐름

```text
퀴즈·로드맵 생성 코드
        │ Micrometer Timer / Counter / DistributionSummary
        ▼
Spring Boot /actuator/prometheus
        │ 15초마다 pull
        ▼
Prometheus (최근 15일 보관)
```

Prometheus가 중지돼도 퀴즈 생성과 학습 기능은 그대로 동작한다.

## 실행

Spring Boot 백엔드를 기본 포트 `8080`으로 먼저 실행한다.

```bash
cd backend
./gradlew bootRun
```

다른 터미널에서 PostgreSQL과 Prometheus를 실행한다.

```bash
docker compose --profile monitoring up -d
```

| 확인 대상 | 주소 |
| --- | --- |
| Spring Boot Prometheus endpoint | http://127.0.0.1:8080/actuator/prometheus |
| Prometheus UI | http://127.0.0.1:9090 |
| Scrape target 상태 | http://127.0.0.1:9090/targets |

Prometheus만 종료하려면 다음 명령을 사용한다.

```bash
docker compose --profile monitoring stop prometheus
```

Prometheus는 기본 실행에 포함되지 않으며 `monitoring` 프로필을 지정했을 때만 실행된다. UI 포트도 `127.0.0.1`에만 바인딩한다.

## 프로젝트 전용 지표

애플리케이션 코드에서는 Micrometer의 점 표기 이름을 사용하고, Prometheus에서는 밑줄 표기와 단위 suffix로 변환된다.

| Micrometer 지표 | 주요 tag | 의미 |
| --- | --- | --- |
| `auknowlog.ai.request.duration` | `operation`, `model`, `outcome` | 퀴즈·로드맵 OpenAI 호출시간과 성공·실패 |
| `auknowlog.ai.tokens` | `operation`, `model`, `type` | 입력·출력·총·추론·캐시 토큰 |
| `auknowlog.quiz.generation.duration` | `outcome` | 중복 검사와 저장을 포함한 퀴즈 생성 전체 시간 |
| `auknowlog.quiz.generation.attempts` | `outcome` | 퀴즈 한 번을 완성하기 위해 수행한 생성 횟수 |
| `auknowlog.quiz.questions` | `outcome` | 요청·생성·채택·중복 제거·미사용 문제 수 |
| `auknowlog.quiz.semantic.checks` | `outcome` | 임베딩 사용 가능 여부와 인덱스 오류 |

`operation`은 `quiz`, `roadmap`만 사용한다. 문제 처리 `outcome`도 코드에 정의된 제한된 값만 허용한다. `topic`, `question`, `quiz_id`, `request_id`, `document_id`처럼 값이 계속 늘어나는 정보는 tag로 만들지 않아 시계열 카디널리티를 제한한다.

### 문제 결과 값

| `outcome` | 설명 |
| --- | --- |
| `requested` | 사용자가 요청한 목표 문제 수 |
| `generated` | 모델이 반환한 후보 문제 수 |
| `accepted` | 중복 검사와 저장을 통과한 문제 수 |
| `exact_duplicate` | SHA-256 해시로 제외한 정확 중복 |
| `semantic_duplicate` | pgvector 유사도로 제외한 의미 중복 |
| `feedback_duplicate` | 반복 문제 피드백 기준으로 제외한 문제 |
| `save_conflict` | 동시 저장 등의 이유로 유일성 제약에 걸린 문제 |
| `unused` | 보충 생성 후 목표 수를 이미 채워 검사하지 않은 후보 |

## PromQL 확인 예시

백엔드 수집 상태:

```promql
up{job="auknowlog-backend"}
```

최근 7일 채택·중복 문제 수:

```promql
sum by (outcome) (
  increase(auknowlog_quiz_questions_total{
    outcome=~"accepted|exact_duplicate|semantic_duplicate|feedback_duplicate"
  }[7d])
)
```

최근 7일 AI 호출 결과:

```promql
sum by (operation, outcome) (
  increase(auknowlog_ai_request_duration_seconds_count[7d])
)
```

퀴즈 생성 전체 시간 P95:

```promql
histogram_quantile(
  0.95,
  sum by (le) (increase(auknowlog_quiz_generation_duration_seconds_bucket[7d]))
)
```

사용량이 적을 때 짧은 5분 구간의 비율은 의미가 없을 수 있으므로 7일 또는 15일 범위를 사용한다. 호출이 발생하지 않은 기간과 실제 0건도 구분해서 해석해야 한다.

## 보안과 데이터 책임

- `/actuator/prometheus`에는 질문 본문, API 키, 사용자 응답을 노출하지 않는다.
- Prometheus UI는 Docker에서 `127.0.0.1`에만 공개한다.
- Cloudflare Quick Tunnel과 프런트엔드 프록시에는 `/actuator/**`를 연결하지 않는다.
- Prometheus는 정확한 비용 원장이 아니다. 서버 재시작과 수집 중단이 있어도 남아야 하는 토큰 정보는 `ai_generation_log`를 사용한다.
- 모델 단가와 환율은 변경될 수 있으므로 현재 단계에서는 토큰만 계측하고 금액은 별도 정책 계층에서 계산한다.

## 현재 범위와 다음 단계

현재 완료 범위는 Spring Boot Prometheus registry, endpoint, 퀴즈 품질 커스텀 지표, Docker Compose 선택 프로필과 실제 scrape 설정이다.

다음 단계에서는 문서 기반 비동기 학습 파이프라인을 구현한 뒤 다음 지표를 추가한다.

- 대기·진행 작업 수
- 가장 오래 대기 중인 작업 시간
- 문서 처리 단계별 시간과 실패
- 마지막 작업 성공 시각
- 재시도 및 Circuit Breaker 상태

이후 Grafana provisioning, 경고 규칙, 장애 주입 테스트 순서로 확장한다.
