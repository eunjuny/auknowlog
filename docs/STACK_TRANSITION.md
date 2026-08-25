# Auknowlog 스택 전환 비교

## 결론

이번 전환은 최신 기술을 나열하기 위한 변경이 아니다. 기존 구조에서 “학습 기록의 부재”, “의미 중복 판별의 한계”, “이중 저장 정합성”, “AI 비용 가시성 부족”을 해결하기 위한 변경이다.

| 이전 | 현재 | 바꾼 이유 | 사용 방법 | 검증 방법 |
| --- | --- | --- | --- | --- |
| Gemini 전용 생성 요청 | OpenAI Responses API + Structured Outputs | JSON 후처리보다 서버가 기대하는 퀴즈 스키마를 먼저 강제하기 위해 | JSON Schema, 응답 검증, 제한 재시도 | HTTP 요청·응답을 모킹한 단위 테스트 |
| GPT 생성 결과를 Markdown·Git·Notion으로 외부 저장 | PostgreSQL 학습 도메인 | 저장 자체보다 풀이·오답·재학습이라는 사용자 상태를 남기기 위해 | 퀴즈, 문항, 풀이, 답안, 복습 일정을 관계형 데이터로 저장 | 자료 저장 → 더미 퀴즈 → 풀이 → 오답 복습 예약 통합 테스트 |
| PostgreSQL 해시 + Elasticsearch `match` | PostgreSQL 해시 + pgvector 코사인 유사도 | ES 점수는 어휘 일치 중심이고 DB/ES 이중 쓰기 불일치가 발생할 수 있기 때문 | `question_embedding`에 512차원 벡터를 저장하고 pgvector `<=>` 연산자로 가장 유사한 문항을 찾음 | 임베딩은 모킹. 실제 pgvector SQL은 Docker/Testcontainers CI에서 검증 예정 |
| Elasticsearch + Kibana 2개 컨테이너 | PostgreSQL + pgvector 1개 데이터 서비스 | 원본 이력과 의미 검색 데이터를 함께 트랜잭션으로 관리하고 로컬 운영 부담을 낮추기 위해 | `pgvector/pgvector` PostgreSQL 16 이미지와 Flyway V3 확장 | Compose 이미지·마이그레이션 구성 점검 |
| 컨트롤러에 생성·중복 검사·저장·색인 혼재 | 유스케이스 서비스 + 도메인 서비스 | HTTP 계층과 비즈니스 순서를 분리해 테스트 가능성을 높이기 위해 | `QuizGenerationService`, `LearningService`, `SourceService`로 역할 분리 | 서비스 단위 테스트와 HTTP 통합 테스트 |
| 호출 성공 여부만 로그 확인 | Micrometer 메트릭 + `ai_generation_log` | 비용·성능·실패를 운영 데이터로 남기기 위해 | 모델, 입력/출력/총 토큰, 지연, 실패 유형 기록 | OpenAI 응답 usage 모킹 테스트 |
| 개발자가 실수로 실제 API 호출 가능 | 더미 모드 기본 + 임베딩 opt-in | 테스트/데모 중 의도치 않은 외부 비용을 막기 위해 | 화면 기본은 `/dummy`, 임베딩은 환경 변수로 명시 활성화 | 모든 자동 테스트에서 라이브 API 미사용 |

## 새 스택을 선택한 근거

### PostgreSQL + pgvector

pgvector는 PostgreSQL 안에 벡터를 저장하고 코사인 거리, 최근접 검색, HNSW 인덱스를 제공한다. 이 프로젝트는 대규모 검색 서비스보다 “문제 이력과 그 의미 벡터를 같이 관리하는 학습 서비스”에 가깝다. 별도 검색 클러스터보다 원본 데이터와의 정합성을 우선하는 선택이다.

- 공식 문서: [pgvector](https://github.com/pgvector/pgvector)
- Java에서는 `JdbcTemplate`의 파라미터 바인딩으로 벡터 리터럴을 전달한다. JPA 엔티티에 벡터 타입을 억지로 섞지 않아, 학습 도메인과 검색 인프라를 분리했다.
- 512차원은 `text-embedding-3` 계열이 차원 축소를 지원하는 범위에서 저장 공간과 검색 비용을 고려한 설정값이다. 모델·차원은 설정으로 바꾸며, 유사도 임계값 0.90은 실제 평가셋을 모은 뒤 보정한다.

### Flyway

`ddl-auto=update`는 실행 시점에 데이터베이스를 바꾸어 변경 이력이 남지 않는다. Flyway는 데이터베이스 변경을 리뷰 가능한 SQL 버전으로 고정한다. 신규 환경은 V1부터 적용하고, 기존 개발 DB는 baseline을 통해 이후 변경만 적용한다.

### Actuator + Micrometer

LLM 애플리케이션에서 API가 200을 반환하는 것만으로는 충분하지 않다. 모델·결과별 호출 시간과 실제 토큰을 남겨야 품질과 비용의 균형을 판단할 수 있다. 가격을 소스에 고정하지 않고 토큰을 기록해 가격 정책 변경에도 지표의 의미를 유지한다.

### Docker Compose 유지, Kubernetes 보류

Compose는 개발·포트폴리오 환경을 한 명이 재현하기에 적절하다. 이번 변경으로 의존 서비스가 PostgreSQL 하나로 줄어 Compose의 장점이 더 커졌다. Kubernetes는 트래픽이나 운영 요구가 생긴 후 문제 해결 도구로 도입한다.

## 아직 의도적으로 하지 않은 것

- 벡터 검색을 위해 별도 벡터 DB나 Elasticsearch를 추가하지 않는다.
- Kafka는 실제 이벤트 소비자가 늘어난 이후 Outbox·멱등성·DLQ까지 함께 구현한다.
- 인증/인가 없는 상태에서 Git push·Notion 저장을 핵심 사용자 흐름으로 두지 않는다. 현재 UI에서는 풀이 기록 저장을 기본 흐름으로 사용한다.
- 라이브 OpenAI 호출을 자동 테스트하지 않는다. OpenAI의 Embeddings API는 입력 텍스트를 벡터로 만들고 사용량을 반환하므로, 테스트에는 모킹을 사용한다. [OpenAI Embeddings API](https://developers.openai.com/api/reference/ruby/resources/embeddings/methods/create)
