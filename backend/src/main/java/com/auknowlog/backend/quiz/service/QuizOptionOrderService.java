package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 모델이 정답을 첫 번째 선택지에 치우쳐 반환해도, 정답-보기 설명 쌍을 함께 재배치한다.
 * 각 문항의 보기 순서를 독립적으로 섞어 정답 위치가 일정한 패턴을 만들지 않게 한다.
 */
public final class QuizOptionOrderService {

    private static final int OPTION_COUNT = 4;

    private QuizOptionOrderService() {
    }

    public static QuizResponse shuffleOptionsIndependently(QuizResponse quiz) {
        if (quiz == null || quiz.questions() == null || quiz.questions().isEmpty()) {
            return quiz;
        }
        List<Question> reordered = new ArrayList<>();
        for (Question question : quiz.questions()) {
            reordered.add(shuffleOptions(question));
        }
        return new QuizResponse(quiz.quizId(), quiz.quizTitle(), reordered);
    }

    private static Question shuffleOptions(Question question) {
        int correctIndex = question.options().indexOf(question.correctAnswer());
        if (correctIndex < 0 || question.options().size() != OPTION_COUNT
                || question.optionExplanations() == null || question.optionExplanations().size() != OPTION_COUNT) {
            throw new IllegalArgumentException("정답 보기 재배치에 필요한 문항 계약이 올바르지 않습니다.");
        }

        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < OPTION_COUNT; index++) {
            indexes.add(index);
        }
        Collections.shuffle(indexes, ThreadLocalRandom.current());

        List<String> options = new ArrayList<>(OPTION_COUNT);
        List<String> explanations = new ArrayList<>(OPTION_COUNT);
        for (int sourceIndex : indexes) {
            options.add(question.options().get(sourceIndex));
            explanations.add(question.optionExplanations().get(sourceIndex));
        }
        return new Question(
                question.questionText(), options, question.correctAnswer(), question.explanation(), explanations,
                question.sourceReferences(), question.objectiveKey()
        );
    }
}
