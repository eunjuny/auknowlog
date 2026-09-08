package com.auknowlog.backend.feedback.dto;

import com.auknowlog.backend.feedback.entity.QuestionFeedback;
import com.auknowlog.backend.feedback.entity.QuestionFeedbackType;

import java.time.LocalDateTime;

public record QuestionFeedbackResponse(
        Long feedbackId,
        Long questionId,
        int questionOrder,
        QuestionFeedbackType feedbackType,
        String feedbackTypeLabel,
        String comment,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean updated
) {
    public static QuestionFeedbackResponse from(QuestionFeedback feedback, boolean updated) {
        return new QuestionFeedbackResponse(
                feedback.getId(),
                feedback.getQuestion().getId(),
                feedback.getQuestion().getQuestionOrder(),
                feedback.getFeedbackType(),
                feedback.getFeedbackType().getDisplayName(),
                feedback.getComment(),
                feedback.getStatus(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt(),
                updated
        );
    }
}
