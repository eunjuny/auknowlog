# 잔존 이슈 및 해결 내역

## ✅ 해결된 이슈

### 1. Elasticsearch 연결 실패로 인한 앱 시작 실패
**상태**: ✅ 해결  
**발생 시기**: 2025-12-17  
**문제**: ES가 실행되지 않아도 앱이 시작되어야 함  
**해결**: `QuestionSearchService`에서 ES 연결 실패 시 graceful degradation 처리

---

### 2. MappingConversionException - 날짜 형식 변환 오류
**상태**: ✅ 해결  
**발생 시기**: 2025-12-17  
**문제**: ES에 저장된 날짜 형식(`"2025-12-17"`)과 Java `LocalDateTime` 타입 불일치  
**해결**: `QuestionDocument.createdAt`을 `LocalDate`로 변경, `@Field(format = DateFormat.date)` 추가

---

### 3. 중복 문제 필터링 후 문제 수 부족
**상태**: ✅ 해결  
**발생 시기**: 2025-12-17  
**문제**: 5문제 요청 → 3개 중복 필터링 → 2개만 반환  
**해결**: 원하는 개수가 될 때까지 최대 3회 재시도 로직 추가

---

### 4. Gemini API 503 Service Unavailable
**상태**: ✅ 해결  
**발생 시기**: 2025-12-17  
**문제**: Gemini 모델 과부하 시 503 오류 발생  
**해결**: 
- 자동 재시도 로직 추가 (최대 5회, 지수 백오프)
- `Retry-After` 헤더 존중
- `GeminiOverloadedException` 커스텀 예외 생성
- HTTP 503으로 응답 반환

---

### 5. 토큰 비용 최적화 - 기존 문제 프롬프트 포함
**상태**: ✅ 해결  
**발생 시기**: 2025-12-17  
**문제**: 중복 방지를 위해 재시도 시 API 호출이 과도하게 증가  
**해결**: 
- 같은 주제의 최근 30개 문제만 조회
- 문제 텍스트 앞 50자만 추출하여 프롬프트에 포함
- 첫 시도에만 기존 문제 목록 조회 (재시도 시 제외)

---

## ⚠️ 경고 (기능 영향 없음)

### 1. PostgreSQLDialect 명시 경고
**상태**: ⚠️ 경고 (기능 영향 없음)  
**메시지**: `HHH90000025: PostgreSQLDialect does not need to be specified explicitly`  
**원인**: Spring Boot가 자동으로 감지하므로 명시 불필요  
**조치**: `application.properties`에서 `spring.jpa.properties.hibernate.dialect` 제거 가능 (선택사항)

---

### 2. Bean Validation Provider 없음
**상태**: ⚠️ 경고 (기능 영향 없음)  
**메시지**: `jakarta.validation.NoProviderFoundException: Unable to create a Configuration`  
**원인**: Hibernate Validator 의존성 없음  
**조치**: 필요 시 `build.gradle`에 `spring-boot-starter-validation` 추가 (현재 불필요)

---

### 3. spring.jpa.open-in-view 경고
**상태**: ⚠️ 경고 (기능 영향 없음)  
**메시지**: `spring.jpa.open-in-view is enabled by default`  
**원인**: 기본값 활성화로 인한 경고  
**조치**: 필요 시 `spring.jpa.open-in-view=false` 설정 (현재는 문제 없음)

---

## 🔄 개선 제안 (미해결)

### 1. 문제 풀(Pool) 사전 생성
**우선순위**: 낮음  
**설명**: 백그라운드에서 인기 주제의 문제를 미리 생성해두고 풀에서 제공  
**장점**: 응답 속도 향상, API 호출 분산  
**단점**: 구현 복잡도 증가, 인기 없는 주제는 낭비

---

### 2. 문제 캐시 시스템
**우선순위**: 낮음  
**설명**: 한 번에 많이 생성한 문제 중 사용하지 않은 것들을 캐시  
**장점**: API 호출 횟수 감소  
**단점**: 캐시 관리 로직 필요, 메모리/DB 사용 증가

---

### 3. 난이도/세부 주제로 분산
**우선순위**: 중간  
**설명**: 프롬프트에 "초급/중급/고급", "실무 사례", "트러블슈팅" 등 변형 요소 추가  
**장점**: 같은 주제여도 다양한 문제 생성 가능  
**단점**: 프롬프트 복잡도 증가

---

### 4. Elasticsearch 유사도 검색 성능 개선
**우선순위**: 낮음  
**설명**: 현재 `match` 쿼리 사용, 더 정교한 유사도 검색 (예: `more_like_this`, `fuzzy`) 고려  
**장점**: 더 정확한 중복 감지  
**단점**: ES 쿼리 복잡도 증가

---

### 5. 로깅 레벨 최적화
**우선순위**: 낮음  
**설명**: 현재 `DEBUG` 레벨이 활성화되어 있음 (`logging.level.org.springframework.core.env=DEBUG`)  
**조치**: 프로덕션에서는 `INFO` 또는 `WARN`으로 변경

---

## 📝 참고사항

- **토큰 최적화**: 현재 같은 주제의 최근 30개 문제만 프롬프트에 포함 (각 문제 앞 50자)
- **재시도 로직**: 최대 3회 재시도, 각 시도마다 부족한 개수 × 2만큼 생성 요청
- **중복 체크**: PostgreSQL 해시(정확 일치) + Elasticsearch 유사도(70% 이상) 이중 체크

