package com.auknowlog.backend.quality.dto;

import com.auknowlog.backend.quality.dto.QualityEvaluationSummary.ThresholdMetric;

import java.util.List;

public record DuplicateDatasetResponse(
        long datasetId,
        String datasetKey,
        String title,
        String datasetVersion,
        String sourceType,
        String status,
        int totalSamples,
        int embeddedSamples,
        int duplicateSamples,
        int relatedSamples,
        int distinctSamples,
        Long embeddingInputTokens,
        String embeddingModel,
        List<ThresholdMetric> thresholdMetrics
) {
}
