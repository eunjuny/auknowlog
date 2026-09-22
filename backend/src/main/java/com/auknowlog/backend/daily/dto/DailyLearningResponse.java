package com.auknowlog.backend.daily.dto;

import com.auknowlog.backend.daily.entity.DailyLearningStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DailyLearningResponse(
        Long dailyLearningId, LocalDate learningDate, String articleTitle, String articleUrl,
        LocalDateTime articlePublishedAt, String articleSummary, String supplement,
        List<DailyLearningConcept> concepts, String reviewTopic, int recommendedReviewQuestionCount,
        DailyLearningStatus status, Long reviewQuizId, Long advancedQuizCount, LocalDateTime completedAt
) { }
