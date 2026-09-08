package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRoadmapCreateRequest(
        @NotBlank(message = "학습 주제를 입력해주세요.")
        @Size(max = 120, message = "학습 주제는 120자 이하여야 합니다.")
        String topic,
        @Min(value = 1, message = "학습 기간은 1주 이상이어야 합니다.")
        @Max(value = 52, message = "학습 기간은 52주 이하여야 합니다.")
        Integer durationWeeks,
        @Min(value = 2, message = "AI 로드맵은 2단계 이상이어야 합니다.")
        @Max(value = 8, message = "AI 로드맵은 8단계 이하여야 합니다.")
        Integer stepCount
) {
}
