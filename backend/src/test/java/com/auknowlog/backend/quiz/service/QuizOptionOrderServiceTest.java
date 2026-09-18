package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizOptionOrderServiceTest {

    @Test
    void shufflesEachQuestionAndKeepsExplanationsPairedWithTheirOptions() {
        List<Question> questions = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> new Question(
                        "문제 " + index,
                        List.of("정답 " + index, "오답 A " + index, "오답 B " + index, "오답 C " + index),
                        "정답 " + index,
                        "정답 설명 " + index,
                        List.of("정답 설명 " + index, "오답 A 설명 " + index, "오답 B 설명 " + index, "오답 C 설명 " + index),
                        List.of(),
                        null
                ))
                .toList();

        QuizResponse distributed = QuizOptionOrderService.shuffleOptionsIndependently(new QuizResponse("분포 테스트", questions));

        for (Question question : distributed.questions()) {
            int correctIndex = question.options().indexOf(question.correctAnswer());
            assertThat(question.optionExplanations().get(correctIndex)).isEqualTo(question.explanation());
            assertThat(question.options()).containsExactlyInAnyOrderElementsOf(
                    List.of("정답 " + question.questionText().substring(3), "오답 A " + question.questionText().substring(3),
                            "오답 B " + question.questionText().substring(3), "오답 C " + question.questionText().substring(3)));
        }
    }
}
