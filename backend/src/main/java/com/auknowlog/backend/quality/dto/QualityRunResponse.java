package com.auknowlog.backend.quality.dto;

public record QualityRunResponse(
        long runId,
        String evaluationType,
        String status,
        int candidateCount,
        int reviewRequiredCount,
        Long totalTokens
) {
}
