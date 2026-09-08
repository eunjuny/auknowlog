package com.auknowlog.backend.dashboard.dto;

import java.time.LocalDate;

public record DashboardSummary(
        LocalDate generatedAt,
        LearningDashboardSummary learning,
        AiDashboardSummary ai,
        QualityFeedbackDashboardSummary qualityFeedback
) {
}
