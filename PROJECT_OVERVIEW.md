# Auknowlog 프로젝트 개요 및 기술 스택

## 📖 프로젝트 소개

`auknowlog`는 AI(Google Gemini)를 활용하여 사용자가 입력한 주제에 대한 객관식 퀴즈를 자동으로 생성하고, 이를 통해 학습 및 지식 기록을 돕는 애플리케이션입니다.

## 🏗️ 아키텍처

*   **모노레포(Monorepo)**: 백엔드와 프론트엔드 코드가 하나의 Git 저장소(`auknowlog/`) 내에서 관리됩니다.
*   **REST API**: 백엔드와 프론트엔드 간의 통신은 RESTful API를 통해 이루어집니다.

## 🚀 백엔드 기술 스택 (Backend Tech Stack)

*   **언어**: **Java**
*   **프레임워크**: **Spring Boot**
    *   Java 기반의 웹 애플리케이션을 빠르고 쉽게 개발할 수 있도록 돕는 프레임워크입니다.
*   **빌드 도구**: **Gradle**
    *   프로젝트의 의존성 관리, 빌드, 테스트, 실행 등을 자동화하는 도구입니다.
*   **웹 스택**: **Spring WebFlux (Reactive)**
    *   비동기, 논블로킹 방식으로 고성능 웹 애플리케이션을 구축하기 위한 Spring의 반응형 웹 프레임워크입니다. `WebClient`와 `Mono`를 사용합니다.
*   **HTTP 클라이언트**: **WebClient**
    *   Spring WebFlux에 포함된 비동기 HTTP 클라이언트로, 외부 API(Gemini API) 호출에 사용됩니다.
*   **JSON 처리**: **Jackson**
    *   Java 객체와 JSON 데이터 간의 변환을 담당하는 라이브러리입니다. Spring Boot에 내장되어 있습니다.
*   **API 문서화**: **Springdoc OpenAPI (Swagger UI)**
    *   REST API를 자동으로 문서화하고, 웹 인터페이스(Swagger UI)를 통해 API를 테스트할 수 있도록 돕는 도구입니다.
*   **AI 연동**: **Google Gemini API**
    *   Google의 최신 AI 모델인 Gemini를 사용하여 퀴즈를 생성하는 데 사용됩니다.
*   **설정 관리**: 
    *   `application.properties`: 애플리케이션의 기본 설정 파일입니다.
    *   `application-gemini.properties`: 민감한 정보(API 키)를 별도로 관리하고 `.gitignore`에 추가하여 Git에 커밋되지 않도록 합니다.
    *   `spring.config.import`: `application.properties`에서 다른 설정 파일을 로드하기 위해 사용됩니다.
*   **로깅**: **SLF4J/Logback**
    *   Spring Boot의 기본 로깅 프레임워크로, 애플리케이션의 동작 상태를 기록합니다.
*   **디렉터리 구조**: `controller`, `service`, `dto` 패키지로 역할을 분리하여 코드를 조직화합니다.

## 🎨 프론트엔드 기술 스택 (Frontend Tech Stack)

*   **프레임워크**: **Vue.js**
    *   사용자 인터페이스(UI)를 구축하기 위한 프로그레시브 JavaScript 프레임워크입니다.
*   **빌드 도구**: **Vite**
    *   매우 빠르고 가벼운 프론트엔드 개발 빌드 도구입니다.
*   **패키지 관리자**: **npm**
    *   JavaScript 라이브러리 및 의존성을 관리하는 데 사용됩니다.
*   **HTTP 클라이언트**: **Axios**
    *   브라우저와 Node.js 환경에서 HTTP 요청을 보내기 위한 Promise 기반의 HTTP 클라이언트 라이브러리입니다.
*   **언어**: **JavaScript (Vue 3 Composition API)**
    *   프론트엔드 로직 구현에 사용되는 언어 및 Vue.js의 최신 개발 스타일입니다.
*   **스타일링**: **CSS**
    *   웹 페이지의 디자인과 레이아웃을 정의하는 데 사용됩니다.

## 🔑 주요 개념 및 도구 설명

*   **Git / GitHub**:
    *   **Git**: 소스 코드의 버전 관리를 위한 분산 버전 관리 시스템입니다.
    *   **GitHub**: Git 저장소를 호스팅하고 협업을 지원하는 웹 기반 플랫폼입니다.
*   **Google Cloud Project**:
    *   Google Cloud에서 제공하는 모든 자원(API, 컴퓨팅, 스토리지 등)을 조직하고 관리하는 논리적인 단위입니다. `auknowlog` 애플리케이션의 모든 클라우드 관련 설정과 사용량이 이 프로젝트 안에서 관리됩니다.
*   **결제 계정 (Billing Account)**:
    *   Google Cloud 서비스 사용량에 대한 비용을 추적하고 청구하기 위한 계정입니다. 무료 사용량 내에서도 서비스 사용을 위해서는 프로젝트에 결제 계정 연결이 필수적입니다.
*   **무료 사용량 (Free Tier)**:
    *   Google Cloud 서비스가 제공하는 일정량의 무료 사용 할당량입니다. 이 할당량 내에서는 요금이 부과되지 않습니다.
*   **DNS (Domain Name System)**:
    *   인터넷의 '전화번호부'와 같습니다. 사람이 기억하기 쉬운 도메인 이름(예: `google.com`)을 컴퓨터가 이해하는 IP 주소(예: `172.217.161.4`)로 변환해 줍니다.
*   **Netty**:
    *   Java 기반의 고성능 네트워크 애플리케이션을 개발하기 위한 비동기 이벤트 기반 네트워크 프레임워크입니다. Spring WebFlux의 `WebClient`와 같은 많은 고성능 네트워크 라이브러리들이 내부적으로 Netty를 사용합니다.
*   **Spring Boot 속성 로딩 순서**:
    *   Spring Boot는 여러 위치(환경 변수, 설정 파일, 명령줄 인수 등)에서 설정 속성을 로드하며, 각 위치에는 우선순위가 있습니다. 우선순위가 높은 곳의 값이 낮은 곳의 값을 덮어씁니다.
