# auknowlog

AI를 활용하여 원하는 주제에 대한 객관식 문제를 자동으로 생성하고, 학습 기록을 관리해주는 애플리케이션입니다.

## 📖 프로젝트 소개 (About The Project)

`auknowlog`는 **auto(자동)** + **knowledge(지식)** + **log(기록)**의 합성어입니다. 사용자가 학습하고 싶은 주제를 입력하면, Gemini AI가 해당 주제에 대한 객관식 퀴즈와 설명을 생성해줍니다. 생성된 내용은 Git 또는 Notion에 자동으로 업로드하여 사용자의 학습, 복습, 그리고 지식 기록 과정을 편리하게 만들어주는 것을 목표로 합니다.

## 🛠️ 기술 스택 (Tech Stack)

*   **Backend:** Java, Spring Boot, Gradle
*   **Frontend:** Vue.js
*   **AI:** Google Gemini
*   **Architecture:** REST API

## ✨ 주요 기능 (Features)

*   주제 입력을 통한 AI 객관식 퀴즈 자동 생성
*   생성된 퀴즈 및 설명 확인 기능
*   (예정) Git 연동을 통한 자동 업로드
*   (예정) Notion 연동을 통한 자동 업로드

## 🚀 시작하기 (Getting Started)

### Backend

```bash
# backend 디렉터리로 이동
cd backend

# 애플리케이션 실행
./gradlew bootRun
```

### Frontend

```bash
# frontend 디렉터리로 이동
cd frontend

# 의존성 설치
npm install

# 개발 서버 실행
npm run dev
```
