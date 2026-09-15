package com.auknowlog.backend.quality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ObjectiveEvaluationRequest(@NotNull @Positive Long roadmapStepId) {
}
