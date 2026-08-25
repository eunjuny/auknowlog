package com.auknowlog.backend.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AttemptAnswerRequest(
        @Min(value = 1, message = "문항 순서는 1 이상이어야 합니다.")
        int questionOrder,
        @NotBlank(message = "선택한 답을 입력해주세요.")
        String selectedAnswer
) {
}
