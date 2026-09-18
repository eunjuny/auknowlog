package com.auknowlog.backend.learning.service;

import com.auknowlog.backend.learning.dto.ReviewAnswerRequest;
import com.auknowlog.backend.learning.dto.ReviewAnswerResponse;
import com.auknowlog.backend.learning.dto.ReviewQueueItem;
import com.auknowlog.backend.learning.dto.ReviewQueueResponse;
import com.auknowlog.backend.learning.dto.ReviewRegistrationRequest;
import com.auknowlog.backend.learning.dto.ReviewRegistrationResponse;
import com.auknowlog.backend.learning.entity.LearningAttemptAnswer;
import com.auknowlog.backend.learning.entity.LearningQuestion;
import com.auknowlog.backend.learning.entity.ReviewAttempt;
import com.auknowlog.backend.learning.entity.ReviewSchedule;
import com.auknowlog.backend.learning.repository.LearningAttemptAnswerRepository;
import com.auknowlog.backend.learning.repository.LearningQuestionRepository;
import com.auknowlog.backend.learning.repository.ReviewAttemptRepository;
import com.auknowlog.backend.learning.repository.ReviewScheduleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ReviewService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewAttemptRepository reviewAttemptRepository;
    private final LearningQuestionRepository learningQuestionRepository;
    private final LearningAttemptAnswerRepository learningAttemptAnswerRepository;
    private final ObjectMapper objectMapper;

    public ReviewService(ReviewScheduleRepository reviewScheduleRepository,
                         ReviewAttemptRepository reviewAttemptRepository,
                         LearningQuestionRepository learningQuestionRepository,
                         LearningAttemptAnswerRepository learningAttemptAnswerRepository,
                         ObjectMapper objectMapper) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewAttemptRepository = reviewAttemptRepository;
        this.learningQuestionRepository = learningQuestionRepository;
        this.learningAttemptAnswerRepository = learningAttemptAnswerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewRegistrationResponse scheduleWrongAnswer(LearningQuestion question, LocalDateTime nextReviewAt) {
        return schedule(question.getId(), nextReviewAt);
    }

    @Transactional
    public ReviewRegistrationResponse registerAfterGrading(ReviewRegistrationRequest request) {
        LearningQuestion question = learningQuestionRepository
                .findByQuizIdAndQuestionOrder(request.quizId(), request.questionOrder())
                .orElseThrow(() -> new NoSuchElementException("복습에 추가할 문항을 찾을 수 없습니다."));
        LearningAttemptAnswer gradedAnswer = learningAttemptAnswerRepository.findByQuestionId(question.getId())
                .orElseThrow(() -> new IllegalArgumentException("채점이 완료된 문항만 복습에 추가할 수 있습니다."));
        return schedule(gradedAnswer.getQuestion().getId(), LocalDateTime.now().plusDays(1));
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse getQueue() {
        LocalDateTime now = LocalDateTime.now();
        List<ReviewQueueItem> items = reviewScheduleRepository
                .findTop100ByStatusOrderByNextReviewAtAsc("PENDING")
                .stream()
                .map(schedule -> toQueueItem(schedule, now))
                .toList();
        long dueCount = items.stream().filter(ReviewQueueItem::due).count();
        return new ReviewQueueResponse(dueCount, items.size() - dueCount, items);
    }

    @Transactional
    public ReviewAnswerResponse answer(Long reviewScheduleId, ReviewAnswerRequest request) {
        ReviewAttempt previousSubmission = reviewAttemptRepository.findBySubmissionId(request.submissionId()).orElse(null);
        if (previousSubmission != null) {
            validateSubmissionOwner(reviewScheduleId, previousSubmission);
            return toAnswerResponse(previousSubmission, true);
        }

        ReviewSchedule schedule = reviewScheduleRepository.findByIdForUpdate(reviewScheduleId)
                .orElseThrow(() -> new NoSuchElementException("복습 일정을 찾을 수 없습니다."));
        previousSubmission = reviewAttemptRepository.findBySubmissionId(request.submissionId()).orElse(null);
        if (previousSubmission != null) {
            validateSubmissionOwner(reviewScheduleId, previousSubmission);
            return toAnswerResponse(previousSubmission, true);
        }
        if (!"PENDING".equals(schedule.getStatus())) {
            throw new IllegalArgumentException("이미 완료되었거나 취소된 복습 일정입니다.");
        }

        LocalDateTime reviewedAt = LocalDateTime.now();
        if (schedule.getNextReviewAt().isAfter(reviewedAt)) {
            throw new IllegalArgumentException("아직 복습 예정일이 되지 않았습니다.");
        }
        String selectedAnswer = request.selectedAnswer().trim();
        List<String> options = readOptions(schedule.getQuestion());
        if (!options.contains(selectedAnswer)) {
            throw new IllegalArgumentException("문항의 선택지 중 하나를 제출해주세요.");
        }

        boolean correct = schedule.getQuestion().getCorrectAnswer().trim().equals(selectedAnswer);
        ReviewSchedule.ReviewTransition transition = schedule.recordAnswer(correct, reviewedAt);
        ReviewAttempt attempt = reviewAttemptRepository.save(new ReviewAttempt(
                schedule,
                request.submissionId(),
                selectedAnswer,
                correct,
                transition.intervalBefore(),
                transition.intervalAfter(),
                schedule.getRepetitionCount(),
                schedule.getLapseCount(),
                schedule.getStatus(),
                reviewedAt,
                transition.nextReviewAt()
        ));
        return toAnswerResponse(attempt, false);
    }

    private void validateSubmissionOwner(Long reviewScheduleId, ReviewAttempt attempt) {
        if (!attempt.getReviewSchedule().getId().equals(reviewScheduleId)) {
            throw new IllegalArgumentException("다른 복습에 이미 사용된 제출 식별자입니다.");
        }
    }

    private ReviewRegistrationResponse schedule(Long questionId, LocalDateTime nextReviewAt) {
        LearningQuestion lockedQuestion = learningQuestionRepository.findByIdForUpdate(questionId)
                .orElseThrow(() -> new NoSuchElementException("복습에 추가할 문항을 찾을 수 없습니다."));
        ReviewSchedule existing = reviewScheduleRepository.findByQuestionIdAndStatus(questionId, "PENDING")
                .orElse(null);
        if (existing != null) {
            return new ReviewRegistrationResponse(existing.getId(), false, existing.getNextReviewAt(), existing.getStatus());
        }
        ReviewSchedule created = reviewScheduleRepository.save(new ReviewSchedule(lockedQuestion, nextReviewAt));
        return new ReviewRegistrationResponse(created.getId(), true, created.getNextReviewAt(), created.getStatus());
    }

    private ReviewQueueItem toQueueItem(ReviewSchedule schedule, LocalDateTime now) {
        LearningQuestion question = schedule.getQuestion();
        return new ReviewQueueItem(
                schedule.getId(),
                question.getQuiz().getTopic(),
                question.getQuestionText(),
                readOptions(question),
                schedule.getNextReviewAt(),
                !schedule.getNextReviewAt().isAfter(now),
                schedule.getIntervalDays(),
                schedule.getRepetitionCount(),
                schedule.getLapseCount()
        );
    }

    private ReviewAnswerResponse toAnswerResponse(ReviewAttempt attempt, boolean duplicate) {
        LearningQuestion question = attempt.getReviewSchedule().getQuestion();
        return new ReviewAnswerResponse(
                attempt.getReviewSchedule().getId(),
                attempt.isCorrect(),
                attempt.getSelectedAnswer(),
                question.getCorrectAnswer(),
                question.getExplanation(),
                readOptionExplanations(question),
                attempt.getNextReviewAt(),
                attempt.getIntervalAfter(),
                attempt.getRepetitionAfter(),
                attempt.getLapseAfter(),
                attempt.getStatusAfter(),
                duplicate
        );
    }

    private List<String> readOptions(LearningQuestion question) {
        try {
            return objectMapper.readValue(question.getOptions(), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 문항 선택지를 읽을 수 없습니다.", exception);
        }
    }

    private List<String> readOptionExplanations(LearningQuestion question) {
        try {
            return objectMapper.readValue(question.getOptionExplanations(), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 보기별 해설을 읽을 수 없습니다.", exception);
        }
    }
}
