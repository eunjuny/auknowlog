package com.auknowlog.backend.dashboard.dto;

import java.time.LocalDateTime;

/**
 * 풀이 이력만으로 계산하는 규칙 기반 추천이다.
 * 추천 대상의 선정은 외부 AI 모델에 위임하지 않아 비용·설명 가능성을 함께 통제한다.
 */
public record LearningRecommendation(
        String topic,
        String priority,
        String title,
        String reason,
        int accuracyPercent,
        long totalQuestions,
        LocalDateTime lastStudiedAt,
        int recommendedQuestionCount
) {
}
