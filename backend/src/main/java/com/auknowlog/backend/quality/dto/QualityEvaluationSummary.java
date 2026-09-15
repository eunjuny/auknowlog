package com.auknowlog.backend.quality.dto;

import java.time.LocalDateTime;
import java.util.List;

public record QualityEvaluationSummary(
        DuplicateSummary duplicate,
        ObjectiveSummary objective,
        List<RunSummary> recentRuns
) {
    public record DuplicateSummary(
            long totalPairs,
            long reviewedPairs,
            long pendingPairs,
            double candidateFloor,
            double automaticDistinctCeiling,
            double automaticDuplicateFloor,
            Double recommendedThreshold,
            List<ThresholdMetric> thresholdMetrics
    ) {
    }

    public record ThresholdMetric(
            double threshold,
            long sampleSize,
            long truePositive,
            long falsePositive,
            long falseNegative,
            long trueNegative,
            Double precisionPercent,
            Double recallPercent,
            Double f1Percent
    ) {
    }

    public record ObjectiveSummary(
            long totalCases,
            long automaticAcceptedCases,
            long pendingCases,
            long reviewedCases,
            Integer provisionalOmissionRatePercent,
            Integer provisionalAlignmentRatePercent,
            Integer humanVerifiedOmissionRatePercent,
            Integer humanVerifiedAlignmentRatePercent
    ) {
    }

    public record RunSummary(
            long id,
            String evaluationType,
            String sourceKey,
            String model,
            String promptVersion,
            String status,
            int candidateCount,
            int reviewRequiredCount,
            Long totalTokens,
            LocalDateTime createdAt
    ) {
    }
}
