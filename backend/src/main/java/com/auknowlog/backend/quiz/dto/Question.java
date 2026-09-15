package com.auknowlog.backend.quiz.dto;

import java.util.List;

public record Question(String questionText,
                       List<String> options,
                       String correctAnswer,
                       String explanation,
                       List<String> sourceReferences,
                       String objectiveKey) {

    public Question(String questionText, List<String> options, String correctAnswer, String explanation) {
        this(questionText, options, correctAnswer, explanation, List.of(), null);
    }

    public Question(String questionText, List<String> options, String correctAnswer, String explanation,
                    List<String> sourceReferences) {
        this(questionText, options, correctAnswer, explanation, sourceReferences, null);
    }
}
