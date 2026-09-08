package com.auknowlog.backend.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuizRequest(
        @NotBlank(message = "학습 주제를 입력해주세요.")
        @Size(max = 120, message = "학습 주제는 120자 이하여야 합니다.")
        String topic,
        @Min(value = 1, message = "문제 수는 1개 이상이어야 합니다.")
        @Max(value = 20, message = "문제 수는 20개 이하여야 합니다.")
        Integer numberOfQuestions,
        @Positive(message = "학습 자료 식별자는 양수여야 합니다.")
        Long sourceId,
        @Positive(message = "학습 로드맵 식별자는 양수여야 합니다.")
        Long roadmapId,
        @Positive(message = "학습 로드맵 단계 식별자는 양수여야 합니다.")
        Long roadmapStepId
) {
    public QuizRequest(String topic, Integer numberOfQuestions) {
        this(topic, numberOfQuestions, null, null, null);
    }
}
