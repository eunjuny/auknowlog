package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoadmapLearningObjectiveDefinition(
        @NotBlank(message = "학습 목표 식별자를 입력해주세요.")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,48}", message = "학습 목표 식별자는 영문, 숫자, 하이픈, 밑줄만 사용할 수 있습니다.")
        String key,
        @NotBlank(message = "학습 목표 이름을 입력해주세요.")
        @Size(max = 120, message = "학습 목표 이름은 120자 이하여야 합니다.")
        String title,
        @Size(max = 500, message = "학습 목표 설명은 500자 이하여야 합니다.")
        String description,
        @Pattern(regexp = "CORE|SUPPORTING", message = "학습 목표 중요도는 CORE 또는 SUPPORTING이어야 합니다.")
        String importance,
        @Min(value = 1, message = "학습 목표별 문제 수는 1개 이상이어야 합니다.")
        @Max(value = 5, message = "학습 목표별 문제 수는 5개 이하여야 합니다.")
        Integer targetQuestionCount
) {
    public String normalizedImportance() {
        return importance == null || importance.isBlank() ? "CORE" : importance;
    }
}
