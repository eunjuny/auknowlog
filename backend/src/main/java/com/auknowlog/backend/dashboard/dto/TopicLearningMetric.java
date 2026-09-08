package com.auknowlog.backend.dashboard.dto;

public record TopicLearningMetric(String topic, long attempts, long correctAnswers, long totalQuestions, int accuracyPercent) {
}
