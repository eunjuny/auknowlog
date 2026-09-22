package com.auknowlog.backend.dashboard.dto;

import java.util.List;

/**
 * 품질 평가 화면의 상세 판정과 분리한 대시보드용 읽기 모델입니다.
 * AI 잠정 지표와 사람이 확정한 지표를 같은 값으로 섞지 않습니다.
 */
public record QualityEvaluationDashboardSummary(
        long duplicateHumanSampleCount,
        long duplicatePendingReviewCount,
        Double recommendedThreshold,
        List<DuplicateThresholdMetric> duplicateThresholdMetrics,
        ObjectiveQualityMetric objectiveQuality
) {
    public record DuplicateThresholdMetric(
            double threshold,
            Double precisionPercent,
            Double recallPercent,
            Double f1Percent
    ) {
    }

    public record ObjectiveQualityMetric(
            long totalCases,
            long humanReviewedCases,
            long pendingReviewCases,
            Integer provisionalOmissionRatePercent,
            Integer provisionalAlignmentRatePercent,
            Integer humanVerifiedOmissionRatePercent,
            Integer humanVerifiedAlignmentRatePercent
    ) {
    }
}
