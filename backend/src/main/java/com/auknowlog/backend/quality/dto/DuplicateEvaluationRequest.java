package com.auknowlog.backend.quality.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DuplicateEvaluationRequest(
        @Min(10) @Max(500) int maxPairs
) {
}
