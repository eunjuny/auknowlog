package com.auknowlog.backend.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record AttemptRequest(
        @NotNull(message = "퀴즈 식별자가 필요합니다.")
        @Positive(message = "퀴즈 식별자는 양수여야 합니다.")
        Long quizId,
        @NotEmpty(message = "제출할 답안이 없습니다.")
        List<@Valid AttemptAnswerRequest> answers
) {
}
