# auknowlog

AI를 활용하여 원하는 주제에 대한 객관식 문제를 자동으로 생성하고, 학습 기록을 관리해주는 애플리케이션입니다.

## 📖 프로젝트 소개

`auknowlog`는 **auto(자동)** + **knowledge(지식)** + **log(기록)**의 합성어입니다.

사용자가 학습하고 싶은 주제를 입력하면, Gemini AI가 해당 주제에 대한 객관식 퀴즈와 설명을 생성합니다. 생성된 퀴즈는 Git 또는 Notion에 자동 저장되며, **유사도 기반 중복 방지** 기능으로 매번 새로운 문제를 받을 수 있습니다.

## 🛠️ 기술 스택

| 구분 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.5, Virtual Threads |
| **Frontend** | Vue.js 3, Vite |
| **AI** | Google Gemini 1.5 Flash |
| **Database** | PostgreSQL 16 (이력 저장) |
| **Search** | Elasticsearch 8.11 (유사도 검색) |
| **Visualization** | Kibana 8.11 |
| **Infra** | Docker Compose |

## ✨ 주요 기능

- 🤖 **AI 퀴즈 자동 생성** - 주제 입력만으로 객관식 퀴즈 생성
- 🔍 **유사도 기반 중복 방지** - Elasticsearch로 비슷한 문제 필터링
- 💾 **다중 저장소 지원** - 로컬 파일, Git, Notion에 저장
- 📊 **Kibana 시각화** - 저장된 문제 조회 및 분석

## 🚀 빠른 시작

### 1. 인프라 실행

```bash
# PostgreSQL + Elasticsearch + Kibana 시작
docker-compose up -d
```

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

### 4. 접속

| 서비스 | URL |
|--------|-----|
| 프론트엔드 | http://localhost:5173 |
| Swagger API | http://localhost:8080/swagger-ui.html |
| Kibana | http://localhost:5601 |

## 📚 문서

자세한 설명은 [개발 가이드](docs/DEVELOPMENT_GUIDE.md)를 참고하세요.

- API 명세
- 데이터베이스 스키마
- Kibana 사용법
- 트러블슈팅

## ⚙️ 환경 설정

`backend/application-api.properties` 파일 생성:

```properties
auknowlog.gemini.api.key=YOUR_GEMINI_API_KEY
```

## 📁 프로젝트 구조

```
auknowlog/
├── backend/                 # Spring Boot 백엔드
│   └── src/main/java/com/auknowlog/backend/
│       ├── quiz/           # 퀴즈 생성 (Controller, Service)
│       ├── question/       # 중복 체크 (PostgreSQL, ES)
│       └── document/       # 저장 (Git, Notion)
├── frontend/               # Vue.js 프론트엔드
├── docs/                   # 문서
└── docker-compose.yml      # 인프라 설정
```
