package com.auknowlog.backend.roadmap.dto;

public record AiRoadmapPreviewResponse(
        RoadmapDefinitionRequest definition,
        Long sourceDocumentId,
        String sourceDocumentTitle,
        String sourceDocumentUri
) {
}
