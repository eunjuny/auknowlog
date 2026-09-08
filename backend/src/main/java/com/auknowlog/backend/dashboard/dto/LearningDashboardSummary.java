package com.auknowlog.backend.dashboard.dto;

import java.util.List;

public record LearningDashboardSummary(
        long totalAttempts,
        long totalQuestions,
        long correctAnswers,
        int accuracyPercent,
        long dueReviewCount,
        List<DailyLearningMetric> dailyActivity,
        List<TopicLearningMetric> topicAccuracy,
        List<LearningRecommendation> recommendations
) {
}
