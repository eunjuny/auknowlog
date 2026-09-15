package com.auknowlog.backend.quiz.dto;

public record QuizObjectiveAllocation(
        Long objectiveId,
        String key,
        String title,
        String description,
        String importance,
        int questionCount
) {
}
