package com.auknowlog.backend.roadmap.dto;

import java.time.LocalDate;

public record RoadmapWeekProgress(
        int weekNumber,
        String topic,
        LocalDate weekStart,
        LocalDate weekEnd,
        int plannedQuestions,
        long completedQuestions,
        int progressPercent,
        String status
) {
}
