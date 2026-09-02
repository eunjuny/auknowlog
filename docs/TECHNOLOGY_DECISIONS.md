# Auknowlog 기술 의사결정 기록

이 문서는 기술 이름을 나열하는 대신, 제품 문제·대안·선택 이유·검증 기준을 기록한다. 현재 구현과 다음 단계 제안은 구분한다.

## 제품 경계

- 사용자는 기술 문서나 Markdown을 학습 자료로 저장하고, 해당 자료 또는 주제로 퀴즈를 만든다.
- 퀴즈를 푼 뒤 결과와 오답 복습 일정을 저장한다.
- AI는 외부 의존성이다. 모델 출력의 형식·지연·사용량을 제품 데이터로 관리하고, 비용이 나는 호출은 명시적으로 활성화한다.

## 이번에 적용한 결정

| 문제 | 선택 | 적용 방식 | 검증 |
| --- | --- | --- | --- |
| ES `match` 점수는 의미 중복을 정확히 표현하지 못하고 PostgreSQL과 이중 쓰기가 발생 | PostgreSQL + pgvector | 원본 이력과 벡터를 같은 PostgreSQL에 저장하고, 코사인 유사도로 의미 중복 후보를 조회 | 실제 pgvector 컨테이너에서 V3, HNSW, 코사인 임계값 검증. 임베딩 장애 시 정확 해시 폴백 검증 |
| H2가 PostgreSQL 확장 타입·연산자를 재현하지 못함 | Testcontainers 2.0.5 | 빠른 `test`와 Docker 기반 `integrationTest`를 분리하고 운영과 같은 pgvector 이미지를 일회용으로 실행 | 로컬 14개 테스트 통과. GitHub Actions가 백엔드 변경마다 `./gradlew check` 실행 |
| 생성 뒤의 사용자가 남기는 학습 가치가 없음 | 학습 도메인 분리 | `learning_quiz`, `learning_question`, `learning_attempt`, `review_schedule`을 추가하고 오답을 다음 날 복습으로 예약 | H2 기반 실제 HTTP 샘플에서 자료 저장 → 더미 퀴즈 → 풀이 → 복습 예약까지 실행 |
| 자료를 넣어도 생성 근거가 남지 않음 | 자료·청크 저장 | 입력 자료를 최대 1,200자 청크로 나누고 생성 요청의 컨텍스트로 전달. 문항은 `sourceReferences`를 반환 | OpenAI 요청은 MockRestServiceServer로 스키마와 프롬프트를 검증 |
| AI 호출 비용·실패 원인을 운영에서 알 수 없음 | Actuator/Micrometer + AI 사용량 원장 | 지연·결과·토큰은 메트릭으로, 생성 호출은 `ai_generation_log`로 저장 | Responses API 사용량을 모킹해 토큰 메트릭과 원장 호출을 단위 테스트 |
| 정형화된 객관식 퀴즈에 고성능 모델 비용이 과도할 수 있음 | GPT-5.4 mini + `reasoning-effort=low` | 기본 모델을 설정으로 분리하고, 구조화 출력·서버 검증·제한 재시도로 품질 하한을 유지 | Responses 요청의 모델·추론 수준, 사용량 메트릭과 원장 기록을 모킹 테스트 |
| DDL 자동 변경은 환경별 결과가 달라짐 | Flyway | 스키마를 V1~V3 마이그레이션으로 관리하고 Hibernate는 `validate`만 수행 | H2에서 V1~V2, Testcontainers의 PostgreSQL에서 V1~V3 검증 |

## 비용 안전 장치

- 프런트엔드의 기본 생성 모드는 `/api/quizzes/dummy`다. OpenAI 호출을 하려면 사용자가 명시적으로 해제해야 한다.
- 임베딩은 기본 활성이다. 다만 화면은 `/dummy`가 기본이므로 사용자가 실제 AI 모드를 선택해야 외부 호출이 발생한다. 필요하면 `AUKNOWLOG_EMBEDDINGS_ENABLED=false`로 즉시 차단한다.
- 테스트는 모든 Responses API·Embeddings API를 MockRestServiceServer 또는 Mockito로 대체한다. 라이브 OpenAI API 호출을 테스트에 포함하지 않는다.
- API 한도·키 오류 시 무료 모델로 자동 전환하지 않는다. 호출은 오류로 종료하고 사용자가 모델/결제 설정을 선택한다.

### 퀴즈 모델 기본값

기본값은 `gpt-5.4-mini`와 `reasoning-effort=low`다. 이 서비스의 기본 작업은 문서 근거를 가진 소수의 객관식 문항 생성으로, 장시간 추론보다 일정한 JSON 형식·지연·비용 관리가 중요하다. GPT-5.4 mini는 고빈도 작업에 적합한 경량 모델이며 Responses API와 Structured Outputs를 지원한다. 더 높은 난도의 서술형 평가나 품질 평가셋에서 실패가 확인되면, 환경 설정만으로 상위 모델을 비교한다. 자동 폴백은 결과 품질과 비용을 예측하기 어렵게 하므로 사용하지 않는다. 계정의 데이터 공유 기반 무료 사용량은 별도 동의·한도 조건에 따른 것이므로 애플리케이션의 비용 보장 수단으로 가정하지 않는다. [OpenAI GPT-5.4 mini 문서](https://developers.openai.com/api/docs/models/gpt-5.4-mini)

## 다음 도입 기준

| 기술 | 지금 보류하는 이유 | 도입 조건 |
| --- | --- | --- |
| Kafka + Outbox | 풀이 이벤트의 독립 소비자가 아직 적다. | 통계, 복습 알림, 분석, 재색인이 독립 배포·재처리를 요구할 때. DB 트랜잭션과 Outbox, 멱등 소비자, DLQ를 함께 구현한다. |
| Kubernetes | 단일 애플리케이션과 PostgreSQL 한 개에는 운영 비용이 크다. | 다중 복제본, 무중단 배포, 자동 확장, 팀 운영이 필요할 때 Helm·readiness/liveness·HPA와 함께 도입한다. |
| Redis | 세션 공유, 고빈도 캐시, 분산 요청 제한 요구가 없다. | 사용자별 생성 제한 또는 캐시 병목이 실제 지표로 확인될 때 TTL·무효화 정책과 함께 도입한다. |
| Prometheus + Grafana | 현 단계에서는 Actuator 메트릭만으로 개발 환경 관측이 가능하다. | 배포 환경에서 장기 추세, 알람, SLO를 운영할 때. |

## 포트폴리오에서 증명할 내용

“LLM을 연결했다”가 아니라, “구조화 출력과 서버 검증으로 생성 형식을 통제하고, 학습 이력·복습·문서 근거·중복 방지·토큰/지연 관측을 하나의 서비스 흐름으로 설계했다”를 핵심으로 제시한다. 측정값은 실제 데모 기간의 p95 지연, 실패율, 평균 토큰, 중복 제거율, 복습 완료율로 채운다.
