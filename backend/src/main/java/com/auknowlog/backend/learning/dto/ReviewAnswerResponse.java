package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewAnswerResponse(
        Long reviewScheduleId,
        boolean correct,
        String selectedAnswer,
        String correctAnswer,
        String explanation,
        List<String> optionExplanations,
        LocalDateTime nextReviewAt,
        int intervalDays,
        int repetitionCount,
        int lapseCount,
        String status,
        boolean duplicateSubmission
) {
}
