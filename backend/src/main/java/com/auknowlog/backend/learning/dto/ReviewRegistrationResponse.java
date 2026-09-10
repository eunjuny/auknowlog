package com.auknowlog.backend.learning.dto;

import java.time.LocalDateTime;

public record ReviewRegistrationResponse(
        Long reviewScheduleId,
        boolean created,
        LocalDateTime nextReviewAt,
        String status
) {
}
