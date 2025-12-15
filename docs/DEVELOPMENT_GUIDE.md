# Auknowlog 개발 가이드

## 📋 목차
1. [프로젝트 개요](#프로젝트-개요)
2. [기술 스택 및 OSS](#기술-스택-및-oss)
3. [아키텍처](#아키텍처)
4. [주요 기능](#주요-기능)
5. [설치 및 실행](#설치-및-실행)
6. [API 명세](#api-명세)
7. [데이터베이스 스키마](#데이터베이스-스키마)

---

## 프로젝트 개요

**auknowlog** = auto(자동) + knowledge(지식) + log(기록)

AI 기반 퀴즈 자동 생성 및 학습 기록 관리 애플리케이션입니다.

---

## 기술 스택 및 OSS

### Backend
| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 21 | 언어 (Virtual Threads) |
| Spring Boot | 3.5.5 | 웹 프레임워크 |
| Spring MVC | - | 동기 처리 + Virtual Threads |
| Spring Data JPA | - | PostgreSQL ORM |
| Spring Data Elasticsearch | - | ES 연동 |
| Gradle | 8.x | 빌드 도구 |

### Frontend
| 기술 | 용도 |
|------|------|
| Vue.js 3 | 프론트엔드 프레임워크 |
| Vite | 빌드 도구 |
| Axios | HTTP 클라이언트 |

### 외부 서비스 & OSS
| OSS | 버전 | 용도 |
|-----|------|------|
| **PostgreSQL** | 16 | 퀴즈 이력 저장 (정확한 중복 체크) |
| **Elasticsearch** | 8.11.0 | 유사도 기반 중복 검색 |
| **Kibana** | 8.11.0 | ES 데이터 시각화/관리 |
| **Google Gemini** | 1.5-flash | AI 퀴즈 생성 |
| **Notion API** | - | 퀴즈 결과 노션 저장 |
| **Docker Compose** | - | 컨테이너 오케스트레이션 |

---

## 아키텍처

```
┌─────────────┐     ┌──────────────────────────────────────┐
│   Frontend  │────▶│              Backend                 │
│   (Vue.js)  │◀────│         (Spring WebFlux)             │
└─────────────┘     └──────────────────────────────────────┘
                              │         │         │
                    ┌─────────┴─────────┴─────────┴─────────┐
                    ▼                   ▼                   ▼
             ┌──────────┐        ┌──────────┐        ┌──────────┐
             │PostgreSQL│        │Elastics- │        │ Gemini   │
             │ (중복체크)│        │  earch   │        │   API    │
             └──────────┘        │(유사도)  │        └──────────┘
                                 └──────────┘
                                      │
                                 ┌──────────┐
                                 │  Kibana  │
                                 │ (시각화) │
                                 └──────────┘
```

### 중복 체크 흐름
1. **Gemini AI**가 퀴즈 생성
2. **Elasticsearch**에서 유사도 검색 (70% 이상이면 중복)
3. 중복 질문 필터링
4. 새 질문을 **PostgreSQL**에 해시 기반 저장
5. 새 질문을 **Elasticsearch**에 인덱싱

---

## 주요 기능

### 1. AI 퀴즈 생성
- 주제 입력 → Gemini API가 객관식 퀴즈 생성
- 문제당 4개 선택지 + 정답 + 해설 제공
- 최대 20문제까지 생성 가능

### 2. 중복 문제 방지 (이중 체크)
| 방식 | 도구 | 설명 |
|------|------|------|
| 정확한 중복 | PostgreSQL | SHA-256 해시 비교 |
| 유사도 중복 | Elasticsearch | match 쿼리로 70% 이상 유사도 필터링 |

### 3. 퀴즈 저장
- **로컬 파일**: Markdown 형식으로 저장
- **Git**: 별도 레포지토리에 자동 커밋/푸시
- **Notion**: 페이지 또는 데이터베이스에 저장

### 4. Kibana 시각화
- 저장된 질문 조회/검색
- 주제별 통계 확인
- 쿼리 직접 실행 (Dev Tools)

---

## 설치 및 실행

### 사전 요구사항
- Java 17+
- Node.js 18+
- Docker & Docker Compose

### 1. 인프라 실행 (PostgreSQL + Elasticsearch + Kibana)

```bash
cd /Users/yeob-eunjun/eunjuny/project/auknowlog
docker-compose up -d

# 상태 확인
docker-compose ps
```

### 2. 백엔드 실행

```bash
cd backend

# API 키 설정 (application-api.properties)
# auknowlog.gemini.api.key=YOUR_GEMINI_API_KEY

./gradlew bootRun
```

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

### 4. 접속 URL
| 서비스 | URL |
|--------|-----|
| 프론트엔드 | http://localhost:5173 |
| 백엔드 API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Kibana | http://localhost:5601 |
| Elasticsearch | http://localhost:9200 |

---

## API 명세

### 퀴즈 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/quizzes/create` | AI 퀴즈 생성 |
| POST | `/api/quizzes/dummy` | 더미 퀴즈 생성 (테스트용) |
| POST | `/api/quizzes/markdown` | 마크다운 렌더링 |

### 문서 저장 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/documents/save-markdown-raw` | 로컬 파일 저장 |
| POST | `/api/documents/save-quiz-notion` | 노션 저장 |
| POST | `/api/documents/save-quiz-git` | Git 저장 |

### 요청 예시

```json
// POST /api/quizzes/create
{
  "topic": "쿠버네티스 기초",
  "numberOfQuestions": 10
}
```

---

## 데이터베이스 스키마

### PostgreSQL - question_history
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | PK |
| topic | VARCHAR | 주제 |
| question_text | TEXT | 질문 내용 |
| question_hash | VARCHAR(64) | SHA-256 해시 (UNIQUE) |
| options | TEXT | 선택지 (JSON) |
| correct_answer | VARCHAR | 정답 |
| explanation | TEXT | 해설 |
| created_at | TIMESTAMP | 생성일시 |

### Elasticsearch - questions 인덱스
| 필드 | 타입 | 설명 |
|------|------|------|
| id | keyword | 문서 ID |
| topic | keyword | 주제 |
| questionText | text (korean) | 질문 (유사도 검색용) |
| questionHash | keyword | 해시 |
| options | text | 선택지 JSON |
| correctAnswer | text | 정답 |
| explanation | text (korean) | 해설 |
| createdAt | date | 생성일시 |

---

## Kibana 사용법

### 1. 접속
http://localhost:5601 (ES 시작 후 1~2분 대기)

### 2. Data View 생성
1. 좌측 메뉴 → **Management** → **Stack Management**
2. **Data Views** → **Create data view**
3. Index pattern: `questions*` 입력 → 생성

### 3. 데이터 조회
- **Discover**: 저장된 질문 테이블 조회
- **Dev Tools**: 직접 쿼리 실행

```
GET questions/_search
{
  "query": { "match_all": {} }
}

GET questions/_search
{
  "query": { "match": { "topic": "쿠버네티스" } }
}
```

---

## 환경 설정 파일

### application-api.properties (민감 정보)
```properties
# Gemini API
auknowlog.gemini.api.key=YOUR_API_KEY
auknowlog.gemini.api.url=https://generativelanguage.googleapis.com/v1
auknowlog.gemini.model=gemini-1.5-flash

# Notion API (선택)
auknowlog.notion.api.key=YOUR_NOTION_SECRET
auknowlog.notion.parent.page-id=YOUR_PAGE_ID

# Git (선택)
auknowlog.git.repo-root=/path/to/repo
```

---

## 트러블슈팅

### Elasticsearch 연결 실패
```bash
# ES 상태 확인
curl http://localhost:9200/_cluster/health?pretty

# 로그 확인
docker-compose logs elasticsearch
```

### Kibana 접속 안됨
- ES가 healthy 상태가 될 때까지 대기 (1~2분)
- `docker-compose ps`로 상태 확인

### 퀴즈 생성 실패 (404)
- `application-api.properties`의 모델명 확인
- `gemini-1.5-flash` 또는 `gemini-1.5-flash-latest` 사용

