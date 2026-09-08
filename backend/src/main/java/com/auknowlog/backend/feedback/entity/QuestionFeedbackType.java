package com.auknowlog.backend.feedback.entity;

public enum QuestionFeedbackType {
    INCORRECT_CONTENT("정답 또는 내용이 부정확해요"),
    AMBIGUOUS("질문이 모호해요"),
    EXPLANATION_INSUFFICIENT("해설이 부족해요"),
    DIFFICULTY_TOO_LOW("난이도가 너무 낮아요"),
    DIFFICULTY_TOO_HIGH("난이도가 너무 높아요"),
    OTHER("기타 의견");

    private final String displayName;

    QuestionFeedbackType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
