package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * *.roadmap.json의 애플리케이션 계약이다. Mermaid는 여기서 파생해 그릴 수 있지만,
 * 이 구조화된 정의가 단계 잠금과 진행률 계산의 원본이다.
 */
public record RoadmapDefinitionRequest(
        @NotBlank(message = "로드맵 파일 버전을 입력해주세요.")
        String version,
        @NotBlank(message = "로드맵 이름을 입력해주세요.")
        @Size(max = 120, message = "로드맵 이름은 120자 이하여야 합니다.")
        String title,
        @NotBlank(message = "학습 주제를 입력해주세요.")
        @Size(max = 120, message = "학습 주제는 120자 이하여야 합니다.")
        String topic,
        @Size(max = 2000, message = "로드맵 설명은 2,000자 이하여야 합니다.")
        String description,
        @Min(value = 1, message = "학습 기간은 1주 이상이어야 합니다.")
        @Max(value = 52, message = "학습 기간은 52주 이하여야 합니다.")
        Integer durationWeeks,
        @NotEmpty(message = "학습 단계를 한 개 이상 입력해주세요.")
        @Size(max = 10, message = "대주제는 최대 10개까지 만들 수 있습니다.")
        List<@Valid RoadmapStepDefinition> steps
) {
}
