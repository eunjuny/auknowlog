package com.auknowlog.backend.quiz.dto;

import java.util.List;

public record Question(String questionText,
                       List<String> options,
                       String correctAnswer,
                       String explanation,
                       List<String> optionExplanations,
                       List<String> sourceReferences,
                       String objectiveKey) {

    public Question(String questionText, List<String> options, String correctAnswer, String explanation) {
        this(questionText, options, correctAnswer, explanation,
                legacyOptionExplanations(options, correctAnswer, explanation), List.of(), null);
    }

    public Question(String questionText, List<String> options, String correctAnswer, String explanation,
                    List<String> sourceReferences) {
        this(questionText, options, correctAnswer, explanation,
                legacyOptionExplanations(options, correctAnswer, explanation), sourceReferences, null);
    }

    /** 기존 호출부와 데모 문항은 보기별 설명을 기본값으로 보완한다. */
    public Question(String questionText, List<String> options, String correctAnswer, String explanation,
                    List<String> sourceReferences, String objectiveKey) {
        this(questionText, options, correctAnswer, explanation,
                legacyOptionExplanations(options, correctAnswer, explanation), sourceReferences, objectiveKey);
    }

    private static List<String> legacyOptionExplanations(List<String> options, String correctAnswer, String explanation) {
        if (options == null) {
            return List.of();
        }
        return options.stream()
                .map(option -> option != null && option.equals(correctAnswer)
                        ? explanation
                        : "이 선택지는 문제의 조건 또는 핵심 개념과 일치하지 않습니다.")
                .toList();
    }
}
