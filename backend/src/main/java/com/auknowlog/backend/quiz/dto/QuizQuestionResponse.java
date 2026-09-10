package com.auknowlog.backend.quiz.dto;

import java.util.List;

/**
 * 풀이 전 클라이언트에 공개해도 되는 문항 정보다.
 * 정답과 해설은 의도적으로 포함하지 않는다.
 */
public record QuizQuestionResponse(
        String questionText,
        List<String> options,
        List<String> sourceReferences
) {
    public static QuizQuestionResponse from(Question question) {
        return new QuizQuestionResponse(
                question.questionText(),
                question.options(),
                question.sourceReferences()
        );
    }
}
