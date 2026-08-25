# Auknowlog 프로젝트 개요 및 기술 스택

## 프로젝트 소개

`auknowlog`는 사용자가 입력한 학습 주제로 객관식 퀴즈를 생성하고, 풀이 결과를 Markdown·Git·Notion에 기록하는 학습 기록 애플리케이션입니다. 생성 단계에는 OpenAI Responses API를 사용하고, 동일·유사 문제를 저장 전에 걸러 반복 학습의 품질을 높입니다.

## 아키텍처

```
Vue 3 + Vite → Spring MVC API → OpenAI Responses API
                          ├→ PostgreSQL (정확 중복·이력)
                          ├→ Elasticsearch (유사도 검색)
                          └→ Markdown / Git / Notion (학습 기록)
```

## 백엔드

- Java 21, Spring Boot 3.5, Spring MVC, Virtual Threads
- Spring Data JPA + PostgreSQL 16, Spring Data Elasticsearch 8.11
- `RestClient`로 OpenAI Responses API 호출
- Structured Outputs(JSON Schema)와 서버 측 응답 검증으로 퀴즈 형식을 보장
- Springdoc OpenAPI(Swagger UI), Jackson, SLF4J/Logback
- `application-api.properties` 또는 `OPENAI_API_KEY` 환경 변수로 시크릿 관리

## 프런트엔드

- Vue 3 Composition API, Vite, Axios, CSS
- Vite 프록시로 `/api` 요청을 백엔드에 전달

## 운영 원칙

- API 키는 저장소에 커밋하지 않고 환경 변수 또는 시크릿 매니저로 주입합니다.
- AI의 형식 보장은 JSON Schema에 맡기되, 문항 수·선택지 수·정답 일치 여부를 서버에서도 검증합니다.
- 일시적인 429/502/503/504 응답만 지수 백오프로 제한 재시도하고, 사용자에게는 503으로 명확히 알립니다.
