package com.auknowlog.backend.quality.dto;

import jakarta.validation.constraints.NotBlank;

public record ObjectiveReviewRequest(@NotBlank String verdict) {
}
