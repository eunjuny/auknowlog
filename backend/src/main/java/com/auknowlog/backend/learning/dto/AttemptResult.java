package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AttemptResult(
        Long attemptId,
        int totalQuestions,
        int correctAnswers,
        int wrongAnswers,
        int reviewScheduledCount,
        LocalDateTime nextReviewAt,
        List<LearningAttemptQuestionResult> questions
) {
}
