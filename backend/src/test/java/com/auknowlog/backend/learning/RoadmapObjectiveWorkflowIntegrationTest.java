package com.auknowlog.backend.learning;

import com.auknowlog.backend.learning.dto.AttemptAnswerRequest;
import com.auknowlog.backend.learning.dto.AttemptRequest;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.quiz.dto.RoadmapQuizPlan;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapLearningObjectiveDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapStepDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapSubtopicDefinition;
import com.auknowlog.backend.roadmap.service.LearningRoadmapService;
import com.auknowlog.backend.roadmap.service.RoadmapQuizPlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roadmap_objective_workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.target=2",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:h2-learning-schema.sql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "auknowlog.openai.embedding.enabled=false"
})
class RoadmapObjectiveWorkflowIntegrationTest {

    @Autowired
    private LearningRoadmapService roadmapService;

    @Autowired
    private RoadmapQuizPlanningService planningService;

    @Autowired
    private LearningService learningService;

    @Test
    void storesObjectiveCoverageAndPlansOnlyTheRemainingEssentialContent() {
        RoadmapDefinitionRequest definition = new RoadmapDefinitionRequest(
                "1.2", "Kubernetes 핵심", "Kubernetes", "필수 개념을 빠짐없이 검증합니다.", 2,
                List.of(new RoadmapStepDefinition(
                        "foundation", "Kubernetes 기초", "Pod 핵심", "Kubernetes", 3,
                        List.of(),
                        List.of(new RoadmapSubtopicDefinition(
                                "pod", "Pod", "Pod 동작 원리", "Kubernetes Pod", 3,
                                List.of(
                                        new RoadmapLearningObjectiveDefinition(
                                                "lifecycle", "Pod 생명주기", "상태 전이를 판단합니다.", "CORE", 2),
                                        new RoadmapLearningObjectiveDefinition(
                                                "probe", "상태 프로브", "프로브 역할을 구분합니다.", "CORE", 1)
                                )
                        )),
                        List.of()
                ))
        );
        LearningRoadmapSummary created = roadmapService.createStageBased(definition);
        Long stepId = created.steps().getFirst().stepId();

        RoadmapQuizPlan firstPlan = planningService.plan(
                new QuizRequest("Kubernetes Pod", 2, null, created.roadmapId(), stepId));

        assertThat(firstPlan.objectiveAllocations())
                .extracting(allocation -> allocation.key() + ":" + allocation.questionCount())
                .containsExactly("lifecycle:1", "probe:1");

        QuizResponse stored = learningService.storeGeneratedQuiz(
                "Kubernetes Pod", null, created.roadmapId(), stepId,
                new QuizResponse("Pod 필수 개념", List.of(
                        question("Pod가 Pending 상태인 주된 이유는?", "lifecycle"),
                        question("readiness probe 실패 시 동작은?", "probe")
                ))
        );
        learningService.recordAttempt(new AttemptRequest(stored.quizId(), List.of(
                new AttemptAnswerRequest(1, "정답"),
                new AttemptAnswerRequest(2, "오답 A")
        )));

        LearningRoadmapSummary progress = roadmapService.getById(created.roadmapId());
        assertThat(progress.steps().getFirst().learningObjectives())
                .extracting(objective -> objective.key() + ":" + objective.coveredQuestionCount()
                        + ":" + objective.correctQuestionCount())
                .containsExactly("lifecycle:1:1", "probe:1:0");

        RoadmapQuizPlan nextPlan = planningService.plan(
                new QuizRequest("Kubernetes Pod", 5, null, created.roadmapId(), stepId));
        assertThat(nextPlan.questionCount()).isEqualTo(1);
        assertThat(nextPlan.objectiveAllocations())
                .extracting(allocation -> allocation.key() + ":" + allocation.questionCount())
                .containsExactly("lifecycle:1");
    }

    @Test
    void acceptsThirtyQuestionsAsTheCumulativeTargetOfOneLearningUnit() {
        List<RoadmapLearningObjectiveDefinition> objectives = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new RoadmapLearningObjectiveDefinition(
                        "objective-" + index,
                        "핵심 목표 " + index,
                        "학습 범위 " + index + "을 검증합니다.",
                        "CORE",
                        5
                ))
                .toList();
        RoadmapDefinitionRequest definition = new RoadmapDefinitionRequest(
                "1.2", "대규모 소주제", "분산 시스템", "여러 세션으로 학습합니다.", 4,
                List.of(new RoadmapStepDefinition(
                        "distributed-core", "분산 시스템 핵심", "넓은 학습 범위",
                        "분산 시스템 핵심", 30, List.of(), List.of(), objectives
                ))
        );

        LearningRoadmapSummary created = roadmapService.createStageBased(definition);

        assertThat(created.totalPlannedQuestions()).isEqualTo(30);
        assertThat(created.steps().getFirst().questionTarget()).isEqualTo(30);
        assertThat(created.steps().getFirst().learningObjectives()).hasSize(6);
    }

    @Test
    void requiresAnExplicitChoiceAfterGoalsAreCoveredAndAllowsAdditionalPractice() {
        RoadmapDefinitionRequest definition = new RoadmapDefinitionRequest(
                "1.2", "명시적 진행", "Kubernetes", "추가 학습 또는 다음 단계 선택", 2,
                List.of(new RoadmapStepDefinition(
                        "foundation", "기초", "핵심 목표", "Kubernetes 기초", 2,
                        List.of(), List.of(), List.of(
                                new RoadmapLearningObjectiveDefinition(
                                        "pod", "Pod", "Pod 생명주기", "CORE", 2)
                )))
        );
        LearningRoadmapSummary created = roadmapService.createStageBased(definition);
        Long stepId = created.steps().getFirst().stepId();
        QuizResponse stored = learningService.storeGeneratedQuiz(
                "Kubernetes 기초", null, created.roadmapId(), stepId,
                new QuizResponse("기초", List.of(question("Pod 재시작 조건은?", "pod"), question("Pod 상태는?", "pod")))
        );
        learningService.recordAttempt(new AttemptRequest(stored.quizId(), List.of(
                new AttemptAnswerRequest(1, "정답"), new AttemptAnswerRequest(2, "정답")
        )));

        assertThat(roadmapService.getById(created.roadmapId()).steps().getFirst().status())
                .isEqualTo("AWAITING_DECISION");
        assertThatThrownBy(() -> planningService.plan(
                new QuizRequest("Kubernetes 기초", 1, null, created.roadmapId(), stepId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("추가 학습 또는 다음 단계 진행");

        RoadmapQuizPlan additionalPlan = planningService.plan(
                new QuizRequest("Kubernetes 기초", 3, null, created.roadmapId(), stepId, true));
        assertThat(additionalPlan.questionCount()).isEqualTo(3);
        assertThat(additionalPlan.objectiveAllocations())
                .extracting(allocation -> allocation.key() + ":" + allocation.questionCount())
                .containsExactly("pod:3");

        LearningRoadmapSummary advanced = roadmapService.confirmStepAdvance(created.roadmapId(), stepId);
        assertThat(advanced.status()).isEqualTo("COMPLETED");
        assertThat(advanced.steps().getFirst().status()).isEqualTo("COMPLETED");
    }

    @Test
    void deletesOnlyTheRoadmapDefinitionWhenTheUserConfirmsDeletion() {
        LearningRoadmapSummary created = roadmapService.createStageBased(new RoadmapDefinitionRequest(
                "1.2", "삭제 확인", "Java", "관리 화면에서 제거할 로드맵", 2,
                List.of(new RoadmapStepDefinition("basics", "기초", null, "Java 기초", 1, List.of()))
        ));

        roadmapService.deleteRoadmap(created.roadmapId());

        assertThatThrownBy(() -> roadmapService.getById(created.roadmapId()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    private Question question(String text, String objectiveKey) {
        return new Question(
                text,
                List.of("정답", "오답 A", "오답 B", "오답 C"),
                "정답",
                "핵심 개념을 확인합니다.",
                List.of(),
                objectiveKey
        );
    }
}
