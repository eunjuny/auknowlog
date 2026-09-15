package com.auknowlog.backend.roadmap.dto;

import java.util.List;

public record RoadmapStepProgress(
        Long stepId,
        String key,
        String title,
        String description,
        String topic,
        int questionTarget,
        long completedQuestions,
        int progressPercent,
        String status,
        List<String> prerequisiteKeys,
        List<RoadmapLearningObjectiveProgress> learningObjectives,
        long additionalPracticeQuestions,
        boolean advanceConfirmed,
        boolean awaitingDecision
) {
    public RoadmapStepProgress(Long stepId, String key, String title, String description, String topic,
                               int questionTarget, long completedQuestions, int progressPercent,
                               String status, List<String> prerequisiteKeys) {
        this(stepId, key, title, description, topic, questionTarget, completedQuestions, progressPercent,
                status, prerequisiteKeys, List.of(), 0, false, false);
    }

    public RoadmapStepProgress(Long stepId, String key, String title, String description, String topic,
                               int questionTarget, long completedQuestions, int progressPercent,
                               String status, List<String> prerequisiteKeys,
                               List<RoadmapLearningObjectiveProgress> learningObjectives) {
        this(stepId, key, title, description, topic, questionTarget, completedQuestions, progressPercent,
                status, prerequisiteKeys, learningObjectives, 0, false, false);
    }
}
