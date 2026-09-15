package com.auknowlog.backend.roadmap.dto;

import java.util.List;

public record RoadmapSubtopicProgress(
        Long stepId,
        String key,
        String title,
        String description,
        String topic,
        int questionTarget,
        long completedQuestions,
        int progressPercent,
        String status,
        List<RoadmapLearningObjectiveProgress> learningObjectives
) {
    public RoadmapSubtopicProgress(Long stepId, String key, String title, String description, String topic,
                                   int questionTarget, long completedQuestions, int progressPercent,
                                   String status) {
        this(stepId, key, title, description, topic, questionTarget, completedQuestions,
                progressPercent, status, List.of());
    }
}
