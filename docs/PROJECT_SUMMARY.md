## Auknowlog 프로젝트 요약

### 개요

- **목적**: 기술 주제·자료·RSS 기사를 퀴즈, 풀이 기록, 간격 반복, 로드맵 학습으로 연결하는 개인 학습 서비스
- **구성**: 모노레포(Backend: Java 21/Spring Boot MVC, Frontend: Vue 3/Vite)
- **AI**: OpenAI `Responses API`와 Structured Outputs(JSON Schema)

### 주요 흐름

1. 사용자가 주제·자료를 선택하거나, 데일리 학습은 RSS 후보를 개발 직접 관련 → 개발 인접 → IT 확장 순으로 비용 없이 선택합니다.
2. 백엔드는 OpenAI Structured Outputs로 퀴즈·로드맵 초안·데일리 해설을 만들고 비용·출력 한도를 먼저 검사합니다.
3. 서버는 문제 해시와 선택적 pgvector 의미 검색으로 중복을 걸러내고, 생성 응답에서는 정답·해설을 제외합니다.
4. 제출 시 서버가 채점·풀이 저장·오답 복습 예약을 하나의 트랜잭션으로 처리하고 보기별 해설을 반환합니다.
5. 로드맵은 목표별 출제 수를 관리하고, 데일리 학습은 기사 해설·복습·심화 학습을 같은 저장·채점 흐름으로 연결합니다.

### 핵심 코드

- `quiz/service/OpenAiQuizService.java`: OpenAI 요청, 재시도, JSON 응답 검증, Markdown 렌더링
- `quiz/controller/QuizController.java`: 입력 검증과 HTTP 응답
- `quiz/service/QuizGenerationService.java`: 생성 재시도, 중복 판별, 저장·색인 흐름 조립
- `learning/service/LearningService.java`: 퀴즈·풀이·오답 복습 저장
- `source/service/SourceService.java`: 학습 자료 청크 관리
- `embedding/service/*`: 선택적 OpenAI 임베딩과 pgvector 의미 중복 체크
- `document/controller/DocumentController.java`: Markdown·Notion·Git 저장 API
- `daily/service/*`: 개발자 우선 RSS 기사 선택, 데일리 학습 생성·복습 완료 처리
- `quality/service/*`: 중복 임계값과 목표·문항 품질 평가·사람 검토

### 설정

```bash
export OPENAI_API_KEY="your_api_key"
```

선택적으로 `backend/application-api.properties`에서 `auknowlog.openai.api.key`와 모델·추론 수준을 재정의할 수 있습니다. 해당 파일은 Git에 포함하지 않습니다.

### 검증

- OpenAI API 요청의 인증 헤더, Responses API 경로, JSON Schema 형식을 단위 테스트합니다.
- 형식이 잘못된 모델 응답은 컨트롤러 전에 거부합니다.
- 빈 주제는 400으로 반환하는 웹 계층 테스트를 둡니다.
- Flyway 마이그레이션으로 스키마와 정확 중복 제약을 검증합니다.
- Testcontainers의 실제 PostgreSQL + pgvector에서 V3·HNSW·코사인 검색을 검증합니다.
- AI 호출의 지연·결과·토큰 사용량을 Actuator/Micrometer로 기록합니다.
- 자료 저장 → 더미 퀴즈 → 풀이 → 복습 예약을 H2 기반 HTTP 통합 테스트로 검증합니다.
- Vitest로 제출 전 정답 비노출·제출 상태를 검증하고, Playwright Chromium으로 생성·제출·데일리 완료·360px 화면을 고정 API fixture로 검증합니다.
- GitHub Actions는 Java 21 백엔드 `check`와 Node 22 프론트엔드 단위·빌드·E2E를 분리 실행합니다. CI는 OpenAI·RSS·개인 DB를 호출하지 않습니다.

### 다음 개선 우선순위

1. 사용자 인증과 데이터 소유권
2. 기사·학습 자료 문단 단위 근거 인용과 생성 결과 근거성 평가
3. 전체 컨테이너화와 Terraform 기반 제한적 클라우드 배포
