package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewQueueItem(
        Long reviewScheduleId,
        String topic,
        String questionText,
        List<String> options,
        LocalDateTime nextReviewAt,
        boolean due,
        int intervalDays,
        int repetitionCount,
        int lapseCount
) {
}
