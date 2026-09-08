# Langfuse 기반 AI 관측

## 왜 도입했는가

기존 `ai_generation_log`는 모델, 성공 여부, 토큰, 지연 시간을 PostgreSQL에 남긴다. 이는 제품 대시보드의 요약 지표에는 충분하지만, 한 번의 퀴즈 생성에서 어떤 단계가 오래 걸렸는지와 중복으로 인해 몇 번 재생성됐는지는 알기 어렵다.

Langfuse는 OpenTelemetry(OTLP) 표준으로 AI 워크플로의 Trace를 기록한다. 프로젝트는 Langfuse 전용 SDK에 종속되지 않고 Java OpenTelemetry SDK로 Langfuse v4 OTLP/HTTP 엔드포인트를 사용한다. 따라서 나중에 다른 OTLP 백엔드로 보내거나 Langfuse를 자가 호스팅해도 도메인 로직을 바꿀 필요가 없다.

## 기록하는 흐름

```text
quiz-generation (root chain)
├─ source-context-lookup
├─ recent-question-lookup
├─ quiz-model-generation (generation, 모델·토큰·지연시간)
├─ exact-duplicate-check × 문항 수
└─ semantic-duplicate-check × 문항 수
   └─ question-embedding (embedding, 모델·입력 토큰)
```

- 루트 Trace는 요청 문제 수, 자료 제공 여부, 생성 결과 수와 재생성 횟수를 기록한다.
- GPT 생성 Observation은 모델명, reasoning effort, 입력·출력·총 토큰, 반환 문항 수를 기록한다.
- 중복 검사 Observation은 정확/의미 중복 여부와 의미 유사도를 기록한다.
- 임베딩 Observation은 모델, 벡터 차원, 입력 토큰을 기록한다.
- `ai_generation_log`와 대시보드는 계속 PostgreSQL을 제품 데이터의 원장으로 사용한다. Langfuse는 요청 원인 분석, 프롬프트 비교, 평가 실험을 위한 보조 관측 계층이다.

## 비용 없는 기본 동작

Langfuse Cloud Hobby는 신용카드 없이 월 50,000 units와 30일 데이터 보관을 제공한다. unit은 Trace, Observation, Score의 합계다. 이 프로젝트는 한 번의 5문제 생성에서 대체로 10~20여 개의 unit을 만들 수 있으므로, 개인 포트폴리오·학습 사용량에서는 무료 한도 안에서 충분히 운용할 수 있다. 최신 한도는 [Langfuse 가격 정책](https://langfuse.com/pricing)을 확인한다.

자가 호스팅 OSS는 라이선스 사용료가 없지만 ClickHouse, PostgreSQL, Redis, Blob Storage, Web/Worker 컨테이너가 필요하다. 개인 Mac에서 단순 추적을 위해 운영하기에는 무겁기 때문에 이 프로젝트의 기본 선택은 Cloud Hobby다. 자가 호스팅은 데이터 반출 제한이나 충분한 서버 자원이 있을 때만 검토한다.

LLM-as-a-Judge 평가, Playground에서의 모델 호출, 프롬프트 실험은 Langfuse 사용료와 별개로 해당 모델 제공자의 API 비용을 발생시킬 수 있으므로 이번 범위에는 포함하지 않는다.

## 안전한 기본값

```properties
auknowlog.langfuse.enabled=false
auknowlog.langfuse.capture-content=false
```

- 기본값은 비활성화다. 키가 없으면 exporter를 만들지 않으며 외부 HTTP 요청도 없다.
- `capture-content=false`가 기본값이다. 사용자 주제는 SHA-256 앞 8바이트 지문으로만 기록하고, 원문 프롬프트, 학습 자료 조각, 생성된 문제·정답·해설은 Langfuse로 보내지 않는다.
- exporter는 별도 OpenTelemetry `TracerProvider`와 배치 전송을 사용한다. Langfuse 전송 실패·지연은 퀴즈 생성 결과에 영향을 주지 않는다.
- 키는 Git에 포함되지 않는 `backend/application-api.properties` 또는 환경 변수에만 둔다.

## 무료 Cloud Hobby 연결 방법

1. [Langfuse Cloud](https://cloud.langfuse.com)에서 Hobby 프로젝트를 만들고 Public Key와 Secret Key를 발급한다.
2. Git에 포함되지 않는 `backend/application-api.properties`에 다음을 설정한다.

```properties
auknowlog.langfuse.enabled=true
auknowlog.langfuse.public-key=pk-lf-...
auknowlog.langfuse.secret-key=sk-lf-...
# EU Cloud 기본값. US/JP 리전을 선택한 경우 해당 Cloud URL로 변경한다.
auknowlog.langfuse.base-url=https://cloud.langfuse.com
auknowlog.langfuse.environment=development
# 원문 프롬프트·문제 내용은 기본적으로 전송하지 않는다.
auknowlog.langfuse.capture-content=false
```

환경 변수 사용도 가능하다.

```bash
export LANGFUSE_ENABLED=true
export LANGFUSE_PUBLIC_KEY='pk-lf-...'
export LANGFUSE_SECRET_KEY='sk-lf-...'
```

애플리케이션을 다시 시작한 뒤 실제 AI 퀴즈를 생성하면 Langfuse의 Observations 화면에서 `quiz-generation` Trace를 확인할 수 있다. 무료 한도와 사용량을 확인한 뒤, 한도를 넘길 가능성이 생기면 tracing을 다시 `false`로 바꾸면 된다.

## 검증

- `LangfuseTracingServiceTest.disabledTracingDoesNotRequireKeysOrSendRequests`: 기본 비활성 상태에서 키 없이 Trace API를 호출해도 외부 의존성 없이 정상 동작하는지 검증한다.
- `LangfuseTracingServiceTest.exportsRedactedTraceToConfiguredOtlpEndpoint`: 로컬 OTLP 수신 서버로 전송해 v4 endpoint, Basic 인증 헤더, ingestion 버전 헤더, 원문 주제 비포함을 검증한다. 실제 Langfuse Cloud와 OpenAI API는 호출하지 않는다.
- `./gradlew check`: 일반 단위 테스트와 pgvector Testcontainers 통합 테스트를 함께 실행한다.

## 다음 단계

Trace가 쌓인 뒤에만 다음 기능을 추가한다.

1. Langfuse Prompt Management로 퀴즈 생성 프롬프트 버전을 관리한다.
2. Java, Kubernetes, PostgreSQL 등 대표 주제 평가셋을 만들고 프롬프트·모델 변경 전후를 비교한다.
3. 품질 기준이 명확해진 뒤에만 LLM-as-a-Judge 또는 규칙 기반 평가를 도입한다.
