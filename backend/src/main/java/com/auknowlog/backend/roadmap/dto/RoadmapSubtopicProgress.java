package com.auknowlog.backend.roadmap.dto;

public record RoadmapSubtopicProgress(
        Long stepId,
        String key,
        String title,
        String description,
        String topic,
        int questionTarget,
        long completedQuestions,
        int progressPercent,
        String status
) {
}
