package com.auknowlog.backend.dashboard.dto;

import java.util.List;

public record AiDashboardSummary(
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        long totalTokens,
        int successRatePercent,
        long averageLatencyMs,
        long p95LatencyMs,
        long storedQuestionCount,
        List<DailyAiMetric> dailyActivity,
        List<ModelAiMetric> modelUsage
) {
}
