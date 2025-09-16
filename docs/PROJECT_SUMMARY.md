## Auknowlog 프로젝트 요약

### 개요
- **목적**: 사용자가 입력한 주제로 AI(Gemini)가 객관식 퀴즈를 생성하고, 결과를 Markdown/Notion에 저장해 학습 기록을 돕는 애플리케이션
- **구성**: 모노레포(Backend: Spring Boot WebFlux, Frontend: Vue 3 + Vite)

### 디렉터리 구조
- `backend/`
  - `src/main/java/com/auknowlog/backend/quiz/`
    - `controller/QuizController.java`: 퀴즈 생성/더미/마크다운 렌더링 API
    - `service/GeminiService.java`: Gemini 호출, 로컬 마크다운 렌더링
    - `dto/*`: `QuizRequest`, `QuizResponse`, `Question` 등
  - `src/main/java/com/auknowlog/backend/document/`
    - `controller/DocumentController.java`: 마크다운 파일 저장, 노션 저장 API
    - `service/DocumentService.java`: 파일 시스템에 마크다운 저장
    - `service/NotionService.java`: Notion 페이지 생성(마크다운 → 블록 변환)
  - `src/main/resources/saved_quizzes/`: 마크다운 저장 디렉터리
  - `application.properties`: 기본 설정. `spring.config.import=optional:file:./application-api.properties`
  - `application-api.properties`: API 키/시크릿 등 민감 값(로컬/비공개 관리 권장)
- `frontend/`
  - `src/components/QuizGenerator.vue`: 메인 UI(퀴즈 생성/풀이/저장)
  - `vite.config.js`: `/api` 프록시(`http://localhost:8080`)

### 실행 방법
- Backend
```bash
cd backend
./gradlew bootRun
```
- Frontend
```bash
cd frontend
npm install
npm run dev
```
- 개발 중 API 프록시: Vite dev server에서 `/api` → 백엔드 8080으로 프록시

### 설정 및 시크릿
- `backend/src/main/resources/application.properties`
  - `spring.config.import=optional:file:./application-api.properties`
- `backend/application-api.properties` (Git 커밋 금지 권장)
  - `auknowlog.gemini.api.key`, `auknowlog.gemini.api.url`
  - `auknowlog.notion.api.key`
  - `auknowlog.notion.parent.page-id`(기본 Parent Page)
- 권장: 운영/배포 시 환경변수 혹은 시크릿 매니저 사용, 저장소 내 노출 키는 즉시 폐기·교체

### 백엔드 상세
- 기술 스택: Spring WebFlux(논블로킹), Reactor `Mono`, `WebClient`, Jackson
- 주요 서비스
  - `GeminiService`
    - `generateQuiz(String, int) -> Mono<QuizResponse>`: Gemini API 호출
    - `renderQuizMarkdownLocally(Map) -> Mono<String>`: LLM 호출 없이 마크다운 생성
  - `DocumentService`
    - `saveQuizAsMarkdown(QuizResponse)`: DTO를 마크다운으로 저장
    - `saveMarkdownContent(String, String)`: 제목/마크다운을 파일로 저장
  - `NotionService`
    - `createPageWithMarkdown(title, markdown, parentPageId, databaseId, databaseTitleProperty) -> Mono<String>`
    - 마크다운을 단순 규칙(헤더/불릿/문단)으로 Notion Blocks 변환 후 `/v1/pages` 호출
- 주요 API
  - `POST /api/quizzes/create`: 주제·문항수로 퀴즈 생성
  - `POST /api/quizzes/dummy`: 더미 퀴즈 생성(LLM 미호출)
  - `POST /api/quizzes/markdown`: 로컬에서 마크다운 렌더링
  - `POST /api/documents/save-quiz-markdown`: DTO 기반 마크다운 저장
  - `POST /api/documents/save-quiz-markdown-raw`: 확장 payload → 마크다운 → 파일 저장
  - `POST /api/documents/save-quiz-notion`: 확장 payload → 마크다운 → Notion 페이지 생성
- 최근 변경(중요)
  - 컨트롤러에서 `block()` 제거, 메서드 시그니처를 `Mono<ResponseEntity<...>>`로 전환
  - 파일 I/O는 `Schedulers.boundedElastic()`로 오프로딩 → 이벤트 루프 블로킹 오류 해결
  - Notion 저장 기능 구현 및 응답으로 생성 URL/ID 반환

### 프런트엔드 상세
- `QuizGenerator.vue`
  - `generateQuiz()`: `POST /api/quizzes/create`
  - `selectOption(i, j)`: 보기 선택/해제, 즉시 정오 표시
  - `saveQuizAsMarkdown()`: 응답을 확장 payload로 가공해 `POST /api/documents/save-quiz-markdown-raw`
  - `saveQuizToNotion()`: 확장 payload로 `POST /api/documents/save-quiz-notion`
  - 모든 문항에 답했을 때만 저장 버튼 활성화
  - 노션 관련 입력 필드 제거(서버 설정값 사용)

### 요청/응답 예시
- 퀴즈 생성 요청
```json
{ "topic": "쿠버네티스", "numberOfQuestions": 10 }
```
- 퀴즈 생성 응답(예)
```json
{
  "quizTitle": "쿠버네티스 퀴즈",
  "questions": [
    { "questionText": "...", "options": ["A","B","C","D"], "correctAnswer": "A", "explanation": "..." }
  ]
}
```
- 저장용 확장 payload(프런트 → 백엔드)
```json
{
  "quizTitle": "쿠버네티스 퀴즈",
  "questions": [
    {
      "questionText": "...",
      "options": ["A","B","C","D"],
      "correctAnswer": "A",
      "explanation": "...",
      "userSelectedIndex": 1,
      "userSelectedAnswer": "B",
      "isCorrect": false
    }
  ],
  "userAnswers": { "0": 1 },
  "stats": { "total": 10, "correct": 7, "wrong": 3 }
}
```

### 운영/에러 처리 노트
- WebFlux 환경에서 블로킹 금지: 컨트롤러에서 `block()` 사용 금지, 리액티브 체인 유지
- 파일 저장 등 블로킹 I/O는 `boundedElastic`에 오프로딩
- Notion 권한: Integration을 대상 Parent Page/Database에 초대해야 생성 가능
- 마크다운 → Notion 변환은 단순 규칙(헤더/불릿/문단)
- 시크릿은 저장소에 포함하지 말고 환경변수/시크릿 매니저 사용

### 향후 개선 아이디어
- 마크다운 → Notion 변환 고도화(코드블록, 표, 체크리스트 등)
- 저장 성공 메시지에서 노션 URL 자동 링크/복사 버튼 제공
- 결과 통계 시각화 및 히스토리 페이지(날짜별/주제별)


