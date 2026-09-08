package com.auknowlog.backend.roadmap.dto;

import java.time.LocalDate;
import java.util.List;

public record LearningRoadmapSummary(
        Long roadmapId,
        String title,
        String topic,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        int durationWeeks,
        int questionsPerWeek,
        long totalPlannedQuestions,
        long completedQuestions,
        int progressPercent,
        Integer currentWeekNumber,
        boolean completed,
        List<RoadmapWeekProgress> weeks,
        String sourceType,
        String description,
        String currentStepKey,
        List<RoadmapStepProgress> steps
) {
}
