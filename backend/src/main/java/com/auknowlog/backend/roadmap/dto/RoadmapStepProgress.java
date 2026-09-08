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
        List<String> prerequisiteKeys
) {
}
