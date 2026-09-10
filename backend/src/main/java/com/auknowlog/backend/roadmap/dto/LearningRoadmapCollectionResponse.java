package com.auknowlog.backend.roadmap.dto;

import java.util.List;

public record LearningRoadmapCollectionResponse(
        List<LearningRoadmapSummary> inProgressRoadmaps,
        List<LearningRoadmapSummary> completedRoadmaps
) {
}
