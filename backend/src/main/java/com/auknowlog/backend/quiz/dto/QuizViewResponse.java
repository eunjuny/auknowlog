package com.auknowlog.backend.quiz.dto;

import java.util.List;

/** 풀이 전용 응답 계약. 채점 정보는 답안 제출 응답에서만 반환한다. */
public record QuizViewResponse(
        Long quizId,
        String quizTitle,
        List<QuizQuestionResponse> questions
) {
    public static QuizViewResponse from(QuizResponse quiz) {
        return new QuizViewResponse(
                quiz.quizId(),
                quiz.quizTitle(),
                quiz.questions().stream().map(QuizQuestionResponse::from).toList()
        );
    }
}
