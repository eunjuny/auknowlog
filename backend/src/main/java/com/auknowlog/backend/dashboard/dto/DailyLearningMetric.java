package com.auknowlog.backend.dashboard.dto;

import java.time.LocalDate;

public record DailyLearningMetric(LocalDate date, long attempts, long correctAnswers, long totalQuestions) {
}
