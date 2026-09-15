package com.auknowlog.backend.quality.dto;

import jakarta.validation.constraints.NotBlank;

public record DuplicateReviewRequest(@NotBlank String verdict) {
}
