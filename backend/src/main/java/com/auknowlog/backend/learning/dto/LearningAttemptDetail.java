package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LearningAttemptDetail(
        Long attemptId,
        String topic,
        String quizTitle,
        int totalQuestions,
        int correctAnswers,
        LocalDateTime submittedAt,
        List<LearningAttemptQuestionResult> questions
) {
}
