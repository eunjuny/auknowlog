package com.auknowlog.backend.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizResponse(Long quizId, String quizTitle, List<Question> questions) {

    public QuizResponse(String quizTitle, List<Question> questions) {
        this(null, quizTitle, questions);
    }

    public QuizResponse withQuizId(Long quizId) {
        return new QuizResponse(quizId, quizTitle, questions);
    }
}
