package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record AiRoadmapCreateRequest(
        @NotBlank(message = "학습 주제를 입력해주세요.")
        @Size(max = 120, message = "학습 주제는 120자 이하여야 합니다.")
        String topic,
        @Min(value = 1, message = "학습 기간은 1주 이상이어야 합니다.")
        @Max(value = 52, message = "학습 기간은 52주 이하여야 합니다.")
        Integer durationWeeks,
        @Positive(message = "학습 자료 ID는 양수여야 합니다.")
        Long sourceId
) {
    public AiRoadmapCreateRequest(String topic, Integer durationWeeks) {
        this(topic, durationWeeks, null);
    }
}
