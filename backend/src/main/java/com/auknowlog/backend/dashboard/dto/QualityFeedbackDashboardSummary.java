package com.auknowlog.backend.dashboard.dto;

import java.util.List;

public record QualityFeedbackDashboardSummary(
        long totalFeedbackCount,
        long openFeedbackCount,
        List<QuestionFeedbackTypeMetric> typeMetrics
) {
}
