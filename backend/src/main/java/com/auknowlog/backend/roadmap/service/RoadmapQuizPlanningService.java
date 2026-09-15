package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.quiz.dto.QuizObjectiveAllocation;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.RoadmapQuizPlan;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapLearningObjectiveProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapStepProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoadmapQuizPlanningService {

    private final LearningRoadmapService learningRoadmapService;

    public RoadmapQuizPlanningService(LearningRoadmapService learningRoadmapService) {
        this.learningRoadmapService = learningRoadmapService;
    }

    @Transactional(readOnly = true)
    public RoadmapQuizPlan plan(QuizRequest request) {
        int requestedCount = request.numberOfQuestions() == null ? 5 : request.numberOfQuestions();
        if (request.roadmapId() == null && request.roadmapStepId() == null) {
            return RoadmapQuizPlan.standard(request.sourceId(), requestedCount);
        }
        if (request.roadmapId() == null) {
            throw new IllegalArgumentException("학습 단계를 사용하려면 로드맵 식별자가 필요합니다.");
        }

        LearningRoadmapSummary roadmap = learningRoadmapService.getById(request.roadmapId());
        if (!"ACTIVE".equals(roadmap.status())) {
            throw new IllegalArgumentException("진행 중인 로드맵에서만 문제를 생성할 수 있습니다.");
        }
        if (roadmap.sourceDocumentId() != null && request.sourceId() != null
                && !roadmap.sourceDocumentId().equals(request.sourceId())) {
            throw new IllegalArgumentException("자료 기반 로드맵은 연결된 학습 자료만 사용할 수 있습니다.");
        }
        Long effectiveSourceId = roadmap.sourceDocumentId() != null
                ? roadmap.sourceDocumentId()
                : request.sourceId();
        if (request.roadmapStepId() == null) {
            return RoadmapQuizPlan.standard(effectiveSourceId, requestedCount);
        }

        RoadmapStepProgress step = roadmap.steps().stream()
                .filter(candidate -> candidate.stepId().equals(request.roadmapStepId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 로드맵에 속한 학습 단계만 사용할 수 있습니다."));
        if (!step.topic().equals(request.topic().trim())) {
            throw new IllegalArgumentException("학습 단계의 주제와 퀴즈 주제가 일치하지 않습니다.");
        }
        if ("LOCKED".equals(step.status())) {
            throw new IllegalArgumentException("선행 학습 단계를 먼저 완료해주세요.");
        }
        if ("COMPLETED".equals(step.status())) {
            throw new IllegalArgumentException("다음 단계 진행을 확정한 학습 단계입니다.");
        }
        boolean additionalPractice = Boolean.TRUE.equals(request.additionalPractice());
        if ("AWAITING_DECISION".equals(step.status()) && !additionalPractice) {
            throw new IllegalArgumentException("필수 학습 목표를 모두 다뤘습니다. 같은 소주제 추가 학습 또는 다음 단계 진행을 선택해주세요.");
        }
        if (step.learningObjectives().isEmpty()) {
            return RoadmapQuizPlan.standard(effectiveSourceId, requestedCount);
        }

        List<RoadmapLearningObjectiveProgress> orderedObjectives = step.learningObjectives().stream()
                .sorted(Comparator
                        .comparing((RoadmapLearningObjectiveProgress objective) -> !"CORE".equals(objective.importance())))
                .toList();
        Map<Long, Integer> allocationByObjectiveId = additionalPractice
                ? allocateAdditionalPractice(orderedObjectives, requestedCount)
                : allocateAcrossCoverageGaps(orderedObjectives, requestedCount);
        List<QuizObjectiveAllocation> allocations = orderedObjectives.stream()
                .filter(objective -> allocationByObjectiveId.getOrDefault(objective.objectiveId(), 0) > 0)
                .map(objective -> new QuizObjectiveAllocation(
                        objective.objectiveId(), objective.key(), objective.title(), objective.description(),
                        objective.importance(), allocationByObjectiveId.get(objective.objectiveId())
                ))
                .toList();
        int plannedCount = allocations.stream().mapToInt(QuizObjectiveAllocation::questionCount).sum();
        if (plannedCount == 0) {
            throw new IllegalArgumentException("이 학습 단계의 필수 학습 목표를 모두 다뤘습니다.");
        }
        return new RoadmapQuizPlan(effectiveSourceId, plannedCount, allocations);
    }

    private Map<Long, Integer> allocateAdditionalPractice(List<RoadmapLearningObjectiveProgress> objectives,
                                                          int requestedCount) {
        Map<Long, Integer> allocationByObjectiveId = new LinkedHashMap<>();
        objectives.forEach(objective -> allocationByObjectiveId.put(objective.objectiveId(), 0));
        int remaining = Math.max(1, Math.min(20, requestedCount));
        int index = 0;
        while (remaining-- > 0) {
            RoadmapLearningObjectiveProgress objective = objectives.get(index++ % objectives.size());
            allocationByObjectiveId.compute(objective.objectiveId(), (ignored, count) -> count + 1);
        }
        return allocationByObjectiveId;
    }

    private Map<Long, Integer> allocateAcrossCoverageGaps(List<RoadmapLearningObjectiveProgress> objectives,
                                                          int requestedCount) {
        Map<Long, Integer> remainingByObjectiveId = new LinkedHashMap<>();
        Map<Long, Integer> allocationByObjectiveId = new LinkedHashMap<>();
        objectives.forEach(objective -> {
            int remaining = Math.max(0, objective.targetQuestionCount()
                    - (int) Math.min(Integer.MAX_VALUE, objective.coveredQuestionCount()));
            remainingByObjectiveId.put(objective.objectiveId(), remaining);
            allocationByObjectiveId.put(objective.objectiveId(), 0);
        });

        int remainingRequest = Math.max(1, Math.min(20, requestedCount));
        remainingRequest = allocatePhase(
                objectives.stream().filter(objective -> "CORE".equals(objective.importance())).toList(),
                remainingByObjectiveId, allocationByObjectiveId, remainingRequest);
        allocatePhase(
                objectives.stream().filter(objective -> !"CORE".equals(objective.importance())).toList(),
                remainingByObjectiveId, allocationByObjectiveId, remainingRequest);
        return allocationByObjectiveId;
    }

    private int allocatePhase(List<RoadmapLearningObjectiveProgress> objectives,
                              Map<Long, Integer> remainingByObjectiveId,
                              Map<Long, Integer> allocationByObjectiveId,
                              int remainingRequest) {
        boolean allocated;
        do {
            allocated = false;
            for (RoadmapLearningObjectiveProgress objective : objectives) {
                if (remainingRequest == 0) break;
                Long objectiveId = objective.objectiveId();
                int remaining = remainingByObjectiveId.get(objectiveId);
                if (remaining <= 0) continue;
                remainingByObjectiveId.put(objectiveId, remaining - 1);
                allocationByObjectiveId.compute(objectiveId, (ignored, count) -> count + 1);
                remainingRequest--;
                allocated = true;
            }
        } while (remainingRequest > 0 && allocated);
        return remainingRequest;
    }
}
