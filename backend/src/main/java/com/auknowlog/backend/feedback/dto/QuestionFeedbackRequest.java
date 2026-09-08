package com.auknowlog.backend.feedback.dto;

import com.auknowlog.backend.feedback.entity.QuestionFeedbackType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionFeedbackRequest(
        @NotNull(message = "퀴즈 정보가 필요합니다.") Long quizId,
        @NotNull(message = "문항 정보가 필요합니다.") @Min(value = 1, message = "문항 순서를 확인해주세요.") Integer questionOrder,
        @NotNull(message = "피드백 유형을 선택해주세요.") QuestionFeedbackType feedbackType,
        @Size(max = 500, message = "의견은 500자 이내로 입력해주세요.") String comment
) {
}
