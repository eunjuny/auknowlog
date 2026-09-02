# pgvector·Testcontainers 통합 테스트

## 결론

이 프로젝트의 중복 방지는 한 가지 방식에 전부 의존하지 않는다. 비용이 들지 않고 확실한 **SHA-256 정확 중복 검사**를 항상 먼저 적용하고, 활성화된 경우에만 **임베딩 + pgvector 의미 중복 검사**를 추가한다. 임베딩 API나 벡터 검색이 실패해도 퀴즈 생성을 중단하지 않고 정확 중복 검사로 계속 동작한다.

이번 개선으로 “pgvector를 구성했다”에서 끝나지 않고 운영과 같은 PostgreSQL 16 + pgvector 이미지를 Testcontainers로 실행해 마이그레이션, 실제 검색 SQL, 장애 폴백을 자동 검증한다.

## pgvector란 무엇인가

pgvector는 PostgreSQL에 벡터 타입과 거리 연산, 최근접 이웃 검색 인덱스를 추가하는 오픈소스 확장이다. 일반 데이터와 임베딩을 같은 DB에서 트랜잭션, 백업, JOIN과 함께 관리할 수 있다.

이 프로젝트에서는 문제 텍스트를 512차원 임베딩으로 바꿔 `question_embedding.embedding vector(512)`에 저장한다. 새 문제 벡터와 기존 벡터의 코사인 거리는 `<=>`로 계산하고, 다음 식으로 유사도를 얻는다.

```text
코사인 유사도 = 1 - 코사인 거리
중복 판정 = 유사도 >= 0.90
```

HNSW는 벡터들을 다층 그래프로 연결하는 근사 최근접 이웃 인덱스다. 완전 탐색보다 검색을 빠르게 하는 대신 인덱스 생성 시간과 메모리를 더 쓰며 결과 재현율을 일부 교환한다. 이 프로젝트의 V3 마이그레이션은 코사인 거리용 `vector_cosine_ops`로 HNSW를 만든다.

- [pgvector 공식 저장소와 거리 연산 문서](https://github.com/pgvector/pgvector)
- [Testcontainers PostgreSQL·pgvector 공식 문서](https://java.testcontainers.org/modules/databases/postgres/)
- [Testcontainers JUnit 5 공식 문서](https://java.testcontainers.org/test_framework_integration/junit_5/)

## 왜 H2 테스트만으로 부족했나

H2의 PostgreSQL 호환 모드는 일반 SQL과 관계형 도메인을 빠르게 테스트하는 데 유용하지만 PostgreSQL 확장인 `CREATE EXTENSION vector`, `vector(512)`, `<=>`, `USING hnsw`를 그대로 실행하지 못한다. 기존 `FlywayMigrationTest`가 V2까지만 적용했던 이유다.

이를 무시하면 다음 문제가 CI를 통과한 뒤 배포 DB에서 처음 발견될 수 있다.

- pgvector 확장이 없는 이미지에서 V3 마이그레이션 실패
- 벡터 차원과 애플리케이션 설정 불일치
- HNSW 인덱스 이름 또는 연산자 클래스 오타
- JDBC 벡터 리터럴과 `CAST(? AS vector)` SQL 오류
- 유사도 임계값 비교 방향 오류

Testcontainers는 테스트 때마다 실제 DB 이미지를 임의 포트에 일회용으로 띄운다. 개발자의 5432/5433 포트나 기존 데이터에 의존하지 않고, 테스트 종료 후 제거되므로 재현성과 격리를 함께 얻는다. 단, Docker가 필요하고 H2보다 느리므로 빠른 단위 테스트와 별도 소스 셋으로 분리했다.

## 중복 방지 흐름

```text
GPT가 후보 문항 생성
        │
        ▼
정규화 문자열의 SHA-256 조회 ── 일치 ──▶ 후보 제외
        │ 불일치
        ▼
임베딩 기능 활성화 여부 확인 ── 비활성/장애 ──▶ 정확 중복 검사만으로 저장
        │ 활성
        ▼
pgvector 코사인 유사도 검색
        │
        ├─ 0.90 이상 ──▶ 의미 중복으로 후보 제외
        └─ 0.90 미만 ──▶ 문항과 임베딩 저장
```

정확 해시는 공백·대소문자·구두점 차이를 정규화한 뒤 SHA-256으로 비교하므로 같은 문장을 저비용으로 막는다. 표현을 바꾼 같은 의미는 해시가 달라질 수 있어 pgvector가 보완한다. 반대로 임베딩은 외부 API 비용과 장애 가능성이 있으므로 정확 해시를 대체하지 않고 선택 계층으로 둔다.

## 테스트 구조

### 1. 빠른 단위·H2 테스트

```bash
cd backend
./gradlew test
```

- OpenAI HTTP는 `MockRestServiceServer` 또는 Mockito로 대체한다.
- 학습 자료 → 더미 퀴즈 → 풀이 → 복습 예약은 H2에서 빠르게 확인한다.
- 임베딩 검사 예외가 발생해도 `QuestionHistoryService.isDuplicate`와 `saveQuestion`이 호출되고 퀴즈가 반환되는지 검증한다.

### 2. 실제 pgvector 통합 테스트

```bash
cd backend
./gradlew integrationTest
```

`PgvectorIntegrationTest`는 다음 순서로 실행된다.

1. `pgvector/pgvector:0.8.6-pg16-bookworm` 컨테이너를 임의 포트에 시작한다.
2. 비어 있는 DB에 Flyway V1~V3를 모두 적용한다.
3. PostgreSQL 카탈로그에서 V3 성공, vector 확장, `vector(512)`와 HNSW 인덱스를 확인한다.
4. 실제 `QuestionVectorRepository.upsert`로 512차원 벡터를 저장한다.
5. 가까운 고정 벡터의 코사인 유사도가 0.90 이상이라 중복으로 차단되는지 확인한다.
6. 직교하는 고정 벡터는 임계값 미만이라 통과하는지 확인한다.
7. 테스트 클래스가 끝나면 임시 컨테이너를 제거한다.

고정 벡터를 사용한 이유는 외부 임베딩 API를 호출하지 않고도 DB 타입·연산자·리포지토리 SQL·임계값 로직을 결정적으로 재현하기 위해서다.

### 3. CI 검증

`.github/workflows/backend-integration.yml`은 백엔드 또는 워크플로 변경이 push/PR에 포함되면 다음 명령을 실행한다.

```bash
./gradlew check --no-daemon
```

Gradle의 `check`가 `test`와 `integrationTest`를 모두 의존하므로 통합 테스트 누락을 막는다. GitHub 호스티드 Linux 러너의 Docker에서 로컬과 같은 pgvector 이미지를 사용한다. 현재 로컬에서 CI와 동일한 명령 경로를 검증했으며, GitHub Actions의 실제 초록 결과는 이 변경을 원격에 push한 뒤 확인해야 한다.

## 검증 결과

2026-09-01 로컬 Docker 환경에서 실행한 결과다.

| 구분 | 결과 |
| --- | --- |
| 단위·H2 테스트 | 12개 통과 |
| 실제 pgvector 통합 테스트 | 2개 통과 |
| 전체 | 14개 통과, 실패 0, 오류 0, 건너뜀 0 |
| 실행 명령 | `./gradlew check --rerun-tasks --no-daemon` |
| 실행 시간 | 20초(해당 로컬 실행 기준) |
| 라이브 OpenAI 호출 | 0회 |
| API 토큰 비용 | 0원 |

## 이 테스트가 증명하지 않는 것

- 고정 벡터 테스트는 실제 임베딩 모델의 한국어 의미 품질을 평가하지 않는다.
- 0.90은 초기 정책값이다. 실제 문항 쌍 평가셋으로 정밀도·재현율을 측정해 조정해야 한다.
- 소량 데이터 테스트는 HNSW의 대규모 검색 성능, 인덱스 재현율과 메모리 사용량을 증명하지 않는다.
- 임베딩 API의 실서비스 네트워크·과금·한도는 모킹하므로 별도의 opt-in 점검 대상이다.
- GitHub Actions 워크플로 파일을 만들었지만 원격 실행 성공은 push 후 확인해야 한다.

이 한계를 명시해야 포트폴리오에서 과장 없이 “실제 DB 호환성을 자동 검증했고, 모델 품질 평가는 다음 평가 단계로 분리했다”고 설명할 수 있다.

## 내일 스터디 진행안

권장 시간은 45분이다.

1. **10분 — pgvector 기본**: 임베딩, 코사인 거리와 유사도, `<=>` 연산자를 설명한다.
2. **10분 — HNSW 선택 근거**: 완전 탐색과 근사 검색, 속도·재현율·메모리의 교환 관계를 살핀다.
3. **15분 — 테스트 코드 읽기**: 컨테이너 생명주기 → Flyway → DB 카탈로그 검증 → 실제 리포지토리 검색 순으로 실행한다.
4. **5분 — 장애 설계**: 임베딩 장애가 핵심 퀴즈 기능 장애로 번지지 않게 SHA-256 폴백을 둔 이유를 토론한다.
5. **5분 — 다음 실험**: 실제 중복/비중복 문항 쌍을 모아 0.90 임계값의 정밀도·재현율을 측정할 계획을 세운다.

스터디에서 답할 핵심 질문은 “왜 벡터 DB를 따로 두지 않았는가?”, “왜 H2와 Testcontainers를 함께 쓰는가?”, “폴백이 가용성을 높이면서 놓칠 수 있는 중복은 무엇인가?” 세 가지다.
