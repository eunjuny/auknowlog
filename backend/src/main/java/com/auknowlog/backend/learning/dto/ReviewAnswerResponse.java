package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;

public record ReviewAnswerResponse(
        Long reviewScheduleId,
        boolean correct,
        String selectedAnswer,
        String correctAnswer,
        String explanation,
        LocalDateTime nextReviewAt,
        int intervalDays,
        int repetitionCount,
        int lapseCount,
        String status,
        boolean duplicateSubmission
) {
}
