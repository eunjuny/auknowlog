package com.auknowlog.backend.learning.dto;

import java.util.List;

public record ReviewQueueResponse(
        long dueCount,
        long upcomingCount,
        List<ReviewQueueItem> reviews
) {
}
