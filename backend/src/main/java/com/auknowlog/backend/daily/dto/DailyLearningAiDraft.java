package com.auknowlog.backend.daily.dto;

import java.util.List;

public record DailyLearningAiDraft(
        String articleSummary,
        String supplement,
        List<DailyLearningConcept> concepts,
        String reviewTopic,
        int recommendedReviewQuestionCount
) { }
