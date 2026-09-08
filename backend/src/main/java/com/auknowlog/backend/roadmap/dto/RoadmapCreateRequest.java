package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoadmapCreateRequest(
        @Size(max = 120, message = "로드맵 이름은 120자 이하여야 합니다.") String title,
        @NotBlank(message = "학습 주제를 입력해주세요.")
        @Size(max = 120, message = "학습 주제는 120자 이하여야 합니다.") String topic,
        @Min(value = 1, message = "학습 기간은 1주 이상이어야 합니다.")
        @Max(value = 12, message = "학습 기간은 12주 이하여야 합니다.") Integer durationWeeks,
        @Min(value = 1, message = "주간 목표는 1문제 이상이어야 합니다.")
        @Max(value = 20, message = "주간 목표는 20문제 이하여야 합니다.") Integer questionsPerWeek
) {
}
