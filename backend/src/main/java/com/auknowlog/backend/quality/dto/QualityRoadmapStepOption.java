package com.auknowlog.backend.quality.dto;

public record QualityRoadmapStepOption(
        long roadmapStepId,
        String roadmapTitle,
        String stepTitle,
        String topic,
        long objectiveCount,
        long questionCount
) {
}
