# 로드맵 진행 선택과 Git 학습 노트 구조

## 목적

로드맵 학습의 완료 기준은 “필수 내용을 최소 한 번 다뤘다”는 기준입니다. 목표 문항 수를 모두 풀었다고 바로 다음 소주제로 이동시키면, 사용자가 약한 부분을 더 연습할 기회를 잃습니다. 반대로 추가 연습을 하더라도 다음 단계가 자동으로 열리면 학습 순서를 설명하기 어렵습니다.

그래서 단계형 로드맵은 **목표 충족**과 **다음 단계 진행 확정**을 별도 상태로 관리합니다. 일반 주제 퀴즈에는 적용하지 않고, `roadmapStepId`가 있는 로드맵 학습에만 적용합니다.

## 진행 흐름

```mermaid
sequenceDiagram
    participant U as 사용자
    participant F as Vue 화면
    participant B as Spring Boot
    participant DB as PostgreSQL

    U->>F: 로드맵 소주제 학습 시작
    F->>B: 문제 생성 요청(roadmapId, roadmapStepId)
    B->>DB: 필수 목표별 출제 이력·선행 단계 확인
    B-->>F: 아직 다루지 않은 목표 우선 문제
    U->>F: 답안 제출
    F->>B: 서버 채점·풀이 저장
    B->>DB: 목표별 문항 수와 정답 수 갱신
    alt 목표 미충족
        B-->>F: 같은 소주제의 남은 목표 출제 가능
    else 목표 충족
        B-->>F: 추가 학습 / 다음 단계 진행 선택 표시
        alt 같은 소주제 추가 학습
            F->>B: additionalPractice=true 문제 생성 요청
            B->>DB: 다음 단계는 잠금 유지, 전체 목표를 순환 배정
        else 다음 단계로 진행
            F->>B: POST /learning-roadmaps/{id}/steps/{stepId}/advance
            B->>DB: advance_confirmed_at 기록
            B-->>F: 다음 소주제 열림
        end
    end
```

## 상태와 서버 규칙

| 상태 | 의미 | 허용 동작 |
| --- | --- | --- |
| `READY` | 선행 단계가 확정되어 아직 풀이가 없음 | 일반 로드맵 문제 생성 |
| `IN_PROGRESS` | 목표 문항을 일부만 풀이함 | 남은 필수 목표 우선 생성 |
| `AWAITING_DECISION` | 목표 문항을 채웠지만 다음 단계 진행을 아직 확정하지 않음 | 같은 소주제 추가 학습 또는 진행 확정 |
| `COMPLETED` | 사용자가 진행을 확정함 | 이후 단계의 선행 조건으로 사용, 해당 단계의 새 문제 생성 차단 |
| `LOCKED` | 선행 단계가 아직 진행 확정되지 않음 | 문제 생성 차단 |

`learning_roadmap_step.advance_confirmed_at`은 사용자가 다음 단계로 넘어가겠다고 선택한 시각입니다. 목표 문항 수를 넘긴 추가 학습량은 진행률을 100% 이상으로 부풀리지 않고 `additionalPracticeQuestions`로 별도 반환합니다. 로드맵 전체 완료도 모든 단계가 `COMPLETED`가 된 뒤에만 처리됩니다.

추가 학습 요청은 이미 끝난 목표를 버리지 않습니다. 필수(`CORE`) 목표를 우선순위로 둔 뒤 전체 목표를 순환 배정해 요청한 문항 수만큼 다시 연습합니다. 모델 호출 여부는 기존과 같습니다. 데모 모드면 비용이 없고, AI 생성 모드에서만 OpenAI 호출 비용이 발생합니다.

## Git 저장 구조

풀이 기록은 PostgreSQL이 원본이며, Git 저장은 사용자가 채점 후 **Git에 저장** 버튼을 눌렀을 때 만드는 사람이 읽기 쉬운 Markdown 사본입니다.

서버는 브라우저가 전송한 제목이나 경로를 저장 위치 결정에 사용하지 않습니다. `quizId`로 저장된 `learning_quiz`를 조회하고 연결된 로드맵·단계 정보를 이용해 경로를 계산합니다.

```text
backend/src/main/resources/saved_quizzes/
└── roadmaps/
    └── roadmap-42-Kubernetes_운영_로드맵/
        ├── step-01-Pod/
        │   ├── quiz-301-20260915_230101.md
        │   └── quiz-304-20260916_081003.md
        └── step-02-Service/
            └── quiz-309-20260916_090414.md
```

- 같은 `roadmapId`는 항상 하나의 `roadmap-{id}-{정리된 제목}` 디렉터리에 누적됩니다.
- 단계형 로드맵은 `step-{순서}-{정리된 제목}` 하위 디렉터리로 구분됩니다.
- 일반 퀴즈는 기존 `saved_quizzes/` 최상위 저장 방식을 유지합니다.
- 파일명에 DB 퀴즈 ID를 넣어 같은 초 단위 저장 충돌을 줄이고, 로드맵 이름을 바꾸더라도 ID 기준 묶음이 바뀌지 않게 합니다.

`GitService`는 파일이 아니라 `saved_quizzes` 전체를 `git subtree split` 대상으로 사용합니다. 따라서 한 단계에 새 노트를 저장해도 다른 로드맵·단계 노트가 `notes` 원격에서 사라지지 않습니다. 외부 Git 명령은 로컬 작업 트리에 커밋을 남기고 `notes/main`으로 푸시하므로, Git 저장은 사용자가 버튼으로 명시 실행한 경우에만 발생합니다.

## 목록 관리와 삭제

로드맵 화면은 `진행 중`과 `완료됨` 탭을 분리합니다. 각 탭은 한 목록만 표시하고 항목을 펼쳐 진행률·단계·학습 목표를 확인하는 방식이라, 여러 로드맵을 동시에 만들었을 때도 완료 이력이 현재 학습을 밀어내지 않습니다.

삭제는 `DELETE /api/learning-roadmaps/{roadmapId}`로 처리하며 확인 대화상자에서 영향을 먼저 안내합니다.

- 삭제 대상: `learning_roadmap`, `learning_roadmap_step`, 단계 의존 관계, 학습 목표 같은 **계획 정의**
- 유지 대상: `learning_quiz`, `learning_question`, `learning_attempt`, `learning_attempt_answer`, 복습 이력
- 유지 방법: 외래 키의 `ON DELETE SET NULL`로 퀴즈의 `roadmap_id`, `roadmap_step_id`, `learning_objective_id` 연결만 해제

즉, 잘못 만든 계획은 관리 화면에서 제거할 수 있지만 “내가 언제 어떤 문제를 풀었는가”라는 학습 이력은 삭제되지 않습니다. 이 선택은 삭제 실수로 학습 통계와 복습 근거가 사라지는 일을 막기 위한 것입니다.

## 검증 범위

- H2 학습 흐름 테스트: 목표 충족 시 `AWAITING_DECISION`, 일반 생성 거절, `additionalPractice=true` 재배정, 진행 확정 뒤 `COMPLETED` 전환을 검증합니다.
- 프론트 빌드: 로드맵 화면과 제출 뒤 선택 카드가 Vue 컴파일을 통과하는지 확인합니다.
- 실제 Git 원격 푸시는 사용자 저장소를 변경하므로 자동 테스트하지 않습니다. 대신 저장 경로는 서버가 `quizId` 관계에서 계산하며, `saved_quizzes` 밖의 파일은 Git 저장 대상으로 거절합니다.
