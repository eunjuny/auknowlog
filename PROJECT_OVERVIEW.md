# Auknowlog 프로젝트 개요

> 전체 기능·데이터 흐름·기술 선택·운영 방법은 [통합 설계·운영 가이드](docs/PROJECT_GUIDE.md)를 기준으로 한다. 이 문서는 빠르게 구조를 파악하기 위한 요약이다.

## 목적

Auknowlog는 기술 주제를 퀴즈로 만들고, 서버 채점 결과를 학습 기록·복습 일정·약점 분석·단계형 로드맵으로 연결하는 개인 학습 서비스다.

## 현재 아키텍처

```text
Vue 3 + Vite
  → Spring Boot API
  ├→ PostgreSQL 16 (학습·복습·로드맵·자료·AI 운영 원장)
  ├→ pgvector (문제 의미 유사도 검사)
  ├→ OpenAI Responses·Embeddings API (명시적 AI 생성 시에만)
  └→ Actuator·Micrometer → Prometheus (선택 실행)
```

개발 환경에서 외부 확인이 필요하면 Cloudflare Quick Tunnel과 Node.js 세션 인증 프록시를 제한된 시간만 사용한다. `quick-email`을 명시적으로 실행하면 인증 검증 뒤 Spring Mail/Gmail SMTP가 고정된 본인 수신 메일로만 URL과 임시 인증 정보를 전달한다. Elasticsearch는 현재 구조에서 사용하지 않는다.

## 핵심 원칙

- 정답·채점·복습 예약은 서버에서 처리해 브라우저 노출과 데이터 왜곡을 막는다.
- 정확 해시와 pgvector를 함께 사용해 같은 문제와 의미가 비슷한 문제를 줄인다.
- AI 로드맵은 바로 저장하지 않고 사용자가 편집·확인한 결과만 저장한다. 각 학습 단위의 필수 목표와 목표별 문제 수를 저장하고, 아직 다루지 않은 목표부터 문제를 생성해 핵심 내용 누락을 줄인다.
- 파일·URL 자료는 보안 검증과 미리보기 확인 뒤 저장한다.
- 시크릿은 Git에 넣지 않고 환경 변수 또는 Git 제외 설정 파일로 주입한다.

## 빠른 실행

```bash
docker compose up -d
cd backend && ./gradlew bootRun
cd frontend && npm install && npm run dev
```

- 화면: `http://localhost:5173`
- Swagger: `http://localhost:8080/swagger-ui.html`
- 전체 백엔드 검증: `cd backend && ./gradlew check`
