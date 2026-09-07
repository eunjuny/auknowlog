package com.auknowlog.backend.learning.dto;

import java.util.List;

public record LearningAttemptHistory(
        List<LearningAttemptSummary> attempts,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
