package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.RoadmapQuizPlan;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapLearningObjectiveProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapStepProgress;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoadmapQuizPlanningServiceTest {

    @Test
    void allocatesQuestionsAcrossUncoveredCoreObjectivesAndUsesRoadmapSource() {
        LearningRoadmapService roadmapService = mock(LearningRoadmapService.class);
        RoadmapStepProgress step = new RoadmapStepProgress(
                7L, "foundation__pod", "Pod", "Pod 핵심", "Kubernetes Pod",
                6, 1, 16, "IN_PROGRESS", List.of(),
                List.of(
                        objective(101L, "lifecycle", "생명주기", "CORE", 2, 1),
                        objective(102L, "probe", "Probe", "CORE", 2, 0),
                        objective(103L, "debug", "장애 진단", "SUPPORTING", 2, 0)
                )
        );
        when(roadmapService.getById(3L)).thenReturn(roadmap(step, 44L));

        RoadmapQuizPlanningService planningService = new RoadmapQuizPlanningService(roadmapService);
        RoadmapQuizPlan coreOnlyPlan = planningService
                .plan(new QuizRequest("Kubernetes Pod", 3, null, 3L, 7L));
        RoadmapQuizPlan plan = planningService
                .plan(new QuizRequest("Kubernetes Pod", 4, null, 3L, 7L));

        assertThat(coreOnlyPlan.objectiveAllocations())
                .extracting(allocation -> allocation.key() + ":" + allocation.questionCount())
                .containsExactly("lifecycle:1", "probe:2");
        assertThat(plan.sourceId()).isEqualTo(44L);
        assertThat(plan.questionCount()).isEqualTo(4);
        assertThat(plan.objectiveAllocations())
                .extracting(allocation -> allocation.key() + ":" + allocation.questionCount())
                .containsExactly("lifecycle:1", "probe:2", "debug:1");
        assertThatThrownBy(() -> planningService.plan(
                new QuizRequest("Kubernetes Pod", 1, 55L, 3L, 7L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연결된 학습 자료만");
    }

    @Test
    void keepsLegacyTopicGenerationWhenTheStepHasNoLearningObjectives() {
        LearningRoadmapService roadmapService = mock(LearningRoadmapService.class);
        RoadmapStepProgress step = new RoadmapStepProgress(
                7L, "legacy", "기존 단계", null, "Java", 5, 0, 0,
                "READY", List.of(), List.of()
        );
        when(roadmapService.getById(3L)).thenReturn(roadmap(step, null));

        RoadmapQuizPlan plan = new RoadmapQuizPlanningService(roadmapService)
                .plan(new QuizRequest("Java", 5, null, 3L, 7L));

        assertThat(plan.objectiveBased()).isFalse();
        assertThat(plan.questionCount()).isEqualTo(5);
    }

    private RoadmapLearningObjectiveProgress objective(Long id, String key, String title, String importance,
                                                        int target, int covered) {
        return new RoadmapLearningObjectiveProgress(
                id, key, title, title + " 설명", importance, target, covered, covered, covered * 100 / target
        );
    }

    private LearningRoadmapSummary roadmap(RoadmapStepProgress step, Long sourceDocumentId) {
        return new LearningRoadmapSummary(
                3L, "Kubernetes", "Kubernetes", "ACTIVE", LocalDate.now(), LocalDate.now().plusWeeks(3),
                4, 6, 6, 1, 16, null, false, List.of(), "AI_GENERATED", null,
                step.key(), List.of(step), List.of(), sourceDocumentId,
                sourceDocumentId == null ? null : "Kubernetes 문서", null
        );
    }
}
