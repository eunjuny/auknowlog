package com.auknowlog.backend.roadmap.dto;

public record RoadmapLearningObjectiveProgress(
        Long objectiveId,
        String key,
        String title,
        String description,
        String importance,
        int targetQuestionCount,
        long coveredQuestionCount,
        long correctQuestionCount,
        int progressPercent
) {
}
