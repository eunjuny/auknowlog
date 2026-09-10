package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoadmapSubtopicDefinition(
        @NotBlank(message = "소주제 식별자를 입력해주세요.")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,48}", message = "소주제 식별자는 영문, 숫자, 하이픈, 밑줄만 사용할 수 있습니다.")
        String key,
        @NotBlank(message = "소주제 이름을 입력해주세요.")
        @Size(max = 120, message = "소주제 이름은 120자 이하여야 합니다.")
        String title,
        @Size(max = 1000, message = "소주제 설명은 1,000자 이하여야 합니다.")
        String description,
        @Size(max = 120, message = "소주제 문제 생성 주제는 120자 이하여야 합니다.")
        String topic,
        @Min(value = 1, message = "소주제별 목표 문제 수는 1개 이상이어야 합니다.")
        @Max(value = 20, message = "소주제별 목표 문제 수는 20개 이하여야 합니다.")
        Integer questionTarget
) {
}
