## Auknowlog 프로젝트 요약

### 개요

- **목적**: 주제 기반 AI 퀴즈 생성과 학습 결과 기록을 한 흐름으로 제공
- **구성**: 모노레포(Backend: Java 21/Spring Boot MVC, Frontend: Vue 3/Vite)
- **AI**: OpenAI `Responses API`와 Structured Outputs(JSON Schema)

### 주요 흐름

1. 사용자가 주제와 문항 수(1~20)를 입력합니다.
2. 백엔드가 OpenAI에 구조화된 퀴즈 생성을 요청합니다.
3. 서버가 제목, 문항 수, 4개 선택지, 정답·해설의 무결성을 검증합니다.
4. PostgreSQL의 해시와 Elasticsearch의 유사도 검색으로 중복을 제거합니다.
5. 풀이 결과를 Markdown, Git 또는 Notion에 저장합니다.

### 핵심 코드

- `quiz/service/OpenAiQuizService.java`: OpenAI 요청, 재시도, JSON 응답 검증, Markdown 렌더링
- `quiz/controller/QuizController.java`: 입력 검증과 중복 제거·저장 흐름
- `question/service/*`: 정확·유사 중복 체크
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

### 다음 개선 우선순위

1. 학습 이력·정답률 대시보드
2. 난이도·문항 유형·세부 주제 필터
3. 배포/CI와 통합 테스트(Testcontainers)
4. Git 저장 기능의 사용자 승인·브랜치 전략 명확화
