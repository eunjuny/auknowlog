## Auknowlog 프로젝트 요약

### 개요

- **목적**: 기술 문서 기반 AI 퀴즈 생성, 풀이 기록, 오답 복습을 한 흐름으로 제공
- **구성**: 모노레포(Backend: Java 21/Spring Boot MVC, Frontend: Vue 3/Vite)
- **AI**: OpenAI `Responses API`와 Structured Outputs(JSON Schema)

### 주요 흐름

1. 사용자가 주제와 문항 수(1~20)를 입력합니다.
2. 백엔드가 OpenAI에 구조화된 퀴즈 생성을 요청합니다.
3. 서버가 제목, 문항 수, 4개 선택지, 정답·해설의 무결성을 검증합니다.
4. PostgreSQL의 해시와 선택적 pgvector 의미 검색으로 중복을 제거합니다.
5. 풀이 결과를 PostgreSQL에 저장하고 오답 문항의 복습 일정을 만듭니다.

### 핵심 코드

- `quiz/service/OpenAiQuizService.java`: OpenAI 요청, 재시도, JSON 응답 검증, Markdown 렌더링
- `quiz/controller/QuizController.java`: 입력 검증과 HTTP 응답
- `quiz/service/QuizGenerationService.java`: 생성 재시도, 중복 판별, 저장·색인 흐름 조립
- `learning/service/LearningService.java`: 퀴즈·풀이·오답 복습 저장
- `source/service/SourceService.java`: 학습 자료 청크 관리
- `embedding/service/*`: 선택적 OpenAI 임베딩과 pgvector 의미 중복 체크
- `document/controller/DocumentController.java`: Markdown·Notion·Git 저장 API

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
- AI 호출의 지연·결과·토큰 사용량을 Actuator/Micrometer로 기록합니다.
- 자료 저장 → 더미 퀴즈 → 풀이 → 복습 예약을 H2 기반 HTTP 통합 테스트로 검증합니다.

### 다음 개선 우선순위

1. 사용자 인증과 학습 이력·정답률 대시보드
2. 실제 pgvector 통합 테스트(Testcontainers)와 GitHub Actions
3. 문항별 출처 검증 평가셋과 유사도 임계값 보정
4. Git/Notion 외부 저장의 사용자 승인·권한·감사 이력
