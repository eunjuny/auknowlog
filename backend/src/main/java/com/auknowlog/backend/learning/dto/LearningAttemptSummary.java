package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;

public record LearningAttemptSummary(
        Long attemptId,
        String topic,
        String quizTitle,
        int totalQuestions,
        int correctAnswers,
        LocalDateTime submittedAt
) {
}
