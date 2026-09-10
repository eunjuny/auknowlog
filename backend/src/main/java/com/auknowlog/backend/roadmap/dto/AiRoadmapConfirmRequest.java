package com.auknowlog.backend.roadmap.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AiRoadmapConfirmRequest(
        @NotNull(message = "저장할 AI 로드맵 미리보기가 필요합니다.")
        @Valid
        RoadmapDefinitionRequest definition,
        @Positive(message = "학습 자료 ID는 양수여야 합니다.")
        Long sourceId
) {
}
