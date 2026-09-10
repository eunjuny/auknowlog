package com.auknowlog.backend.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReviewRegistrationRequest(
        @NotNull(message = "퀴즈 식별자는 필수입니다.")
        @Positive(message = "퀴즈 식별자는 양수여야 합니다.")
        Long quizId,
        @NotNull(message = "문항 순서는 필수입니다.")
        @Min(value = 1, message = "문항 순서는 1 이상이어야 합니다.")
        Integer questionOrder
) {
}
