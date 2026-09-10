package com.auknowlog.backend.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReviewAnswerRequest(
        @NotNull(message = "복습 제출 식별자가 필요합니다.")
        UUID submissionId,
        @NotBlank(message = "선택한 답안을 입력해주세요.")
        String selectedAnswer
) {
}
