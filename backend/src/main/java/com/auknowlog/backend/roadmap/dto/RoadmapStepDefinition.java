package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoadmapStepDefinition(
        @NotBlank(message = "단계 식별자를 입력해주세요.")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "단계 식별자는 영문, 숫자, 하이픈, 밑줄만 사용할 수 있습니다.")
        String key,
        @NotBlank(message = "단계 이름을 입력해주세요.")
        @Size(max = 120, message = "단계 이름은 120자 이하여야 합니다.")
        String title,
        @Size(max = 1000, message = "단계 설명은 1,000자 이하여야 합니다.")
        String description,
        @Size(max = 120, message = "단계 학습 주제는 120자 이하여야 합니다.")
        String topic,
        @Min(value = 1, message = "단계별 목표 문제 수는 1개 이상이어야 합니다.")
        @Max(value = 20, message = "단계별 목표 문제 수는 20개 이하여야 합니다.")
        Integer questionTarget,
        @Size(max = 20, message = "선행 단계는 최대 20개까지 지정할 수 있습니다.")
        List<String> dependsOn
) {
    public List<String> safeDependsOn() {
        return dependsOn == null ? List.of() : dependsOn;
    }
}
