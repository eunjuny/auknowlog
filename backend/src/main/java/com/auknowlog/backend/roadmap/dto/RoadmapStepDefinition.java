package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

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
        @Max(value = 200, message = "대주제 목표 문제 수는 200개 이하여야 합니다.")
        Integer questionTarget,
        @Size(max = 10, message = "선행 대주제는 최대 10개까지 지정할 수 있습니다.")
        List<String> dependsOn,
        @Size(max = 10, message = "대주제별 소주제는 최대 10개까지 만들 수 있습니다.")
        List<@Valid RoadmapSubtopicDefinition> subtopics
) {
    public RoadmapStepDefinition(String key, String title, String description, String topic,
                                 Integer questionTarget, List<String> dependsOn) {
        this(key, title, description, topic, questionTarget, dependsOn, List.of());
    }

    public List<String> safeDependsOn() {
        return dependsOn == null ? List.of() : dependsOn;
    }

    public List<RoadmapSubtopicDefinition> safeSubtopics() {
        return subtopics == null ? List.of() : subtopics;
    }
}
