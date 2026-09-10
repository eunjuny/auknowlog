package com.auknowlog.backend.feedback.service;

import com.auknowlog.backend.feedback.dto.QuestionFeedbackRequest;
import com.auknowlog.backend.feedback.dto.QuestionFeedbackResponse;
import com.auknowlog.backend.feedback.entity.QuestionFeedback;
import com.auknowlog.backend.feedback.entity.QuestionFeedbackType;
import com.auknowlog.backend.feedback.repository.QuestionFeedbackRepository;
import com.auknowlog.backend.learning.entity.LearningQuestion;
import com.auknowlog.backend.learning.repository.LearningQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class QuestionFeedbackService {

    private final LearningQuestionRepository learningQuestionRepository;
    private final QuestionFeedbackRepository questionFeedbackRepository;

    public QuestionFeedbackService(LearningQuestionRepository learningQuestionRepository,
                                   QuestionFeedbackRepository questionFeedbackRepository) {
        this.learningQuestionRepository = learningQuestionRepository;
        this.questionFeedbackRepository = questionFeedbackRepository;
    }

    @Transactional
    public QuestionFeedbackResponse save(QuestionFeedbackRequest request) {
        LearningQuestion question = learningQuestionRepository
                .findByQuizIdAndQuestionOrder(request.quizId(), request.questionOrder())
                .orElseThrow(() -> new NoSuchElementException("해당 퀴즈의 문항을 찾을 수 없습니다."));
        String comment = normalizeComment(request.comment());

        if (request.feedbackType() == QuestionFeedbackType.OTHER && comment == null) {
            throw new IllegalArgumentException("기타 의견을 선택한 경우 내용을 입력해주세요.");
        }

        QuestionFeedback existing = questionFeedbackRepository.findByQuestionId(question.getId()).orElse(null);
        if (existing != null) {
            existing.update(request.feedbackType(), comment);
            return QuestionFeedbackResponse.from(existing, true);
        }

        QuestionFeedback feedback = questionFeedbackRepository.save(
                new QuestionFeedback(question, request.feedbackType(), comment));
        return QuestionFeedbackResponse.from(feedback, false);
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional(readOnly = true)
    public List<String> getSimilarityAvoidanceQuestions(String topic, int limit) {
        if (topic == null || topic.isBlank() || limit <= 0) {
            return List.of();
        }
        return questionFeedbackRepository.findQuestionTextsByTypeAndTopic(
                QuestionFeedbackType.TOO_SIMILAR,
                topic.trim(),
                PageRequest.of(0, Math.min(limit, 10))
        );
    }
}
