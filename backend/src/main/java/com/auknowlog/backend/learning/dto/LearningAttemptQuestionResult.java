package com.auknowlog.backend.learning.dto;

import java.util.List;

public record LearningAttemptQuestionResult(
        int questionOrder,
        String questionText,
        List<String> options,
        String selectedAnswer,
        String correctAnswer,
        String explanation,
        List<String> sourceReferences,
        boolean correct
) {
}
