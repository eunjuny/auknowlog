package com.auknowlog.backend.roadmap.dto;

import java.util.List;

public record RoadmapMajorTopicProgress(
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
        List<RoadmapSubtopicProgress> subtopics,
        List<RoadmapLearningObjectiveProgress> learningObjectives
) {
    public RoadmapMajorTopicProgress(Long stepId, String key, String title, String description, String topic,
                                     int questionTarget, long completedQuestions, int progressPercent,
                                     String status, List<String> prerequisiteKeys,
                                     List<RoadmapSubtopicProgress> subtopics) {
        this(stepId, key, title, description, topic, questionTarget, completedQuestions,
                progressPercent, status, prerequisiteKeys, subtopics, List.of());
    }
}
