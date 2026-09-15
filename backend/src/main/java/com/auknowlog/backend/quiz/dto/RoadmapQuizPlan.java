package com.auknowlog.backend.quiz.dto;

import java.util.List;

public record RoadmapQuizPlan(
        Long sourceId,
        int questionCount,
        List<QuizObjectiveAllocation> objectiveAllocations
) {
    public static RoadmapQuizPlan standard(Long sourceId, int questionCount) {
        return new RoadmapQuizPlan(sourceId, questionCount, List.of());
    }

    public boolean objectiveBased() {
        return objectiveAllocations != null && !objectiveAllocations.isEmpty();
    }
}
