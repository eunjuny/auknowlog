package com.auknowlog.backend.learning.service;

import com.auknowlog.backend.learning.dto.AttemptAnswerRequest;
import com.auknowlog.backend.learning.dto.AttemptRequest;
import com.auknowlog.backend.learning.dto.AttemptResult;
import com.auknowlog.backend.learning.entity.LearningAttempt;
import com.auknowlog.backend.learning.entity.LearningAttemptAnswer;
import com.auknowlog.backend.learning.entity.LearningQuestion;
import com.auknowlog.backend.learning.entity.LearningQuiz;
import com.auknowlog.backend.learning.entity.ReviewSchedule;
import com.auknowlog.backend.learning.repository.LearningAttemptAnswerRepository;
import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.learning.repository.LearningQuestionRepository;
import com.auknowlog.backend.learning.repository.LearningQuizRepository;
import com.auknowlog.backend.learning.repository.ReviewScheduleRepository;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningService {

    private final LearningQuizRepository learningQuizRepository;
    private final LearningQuestionRepository learningQuestionRepository;
    private final LearningAttemptRepository learningAttemptRepository;
    private final LearningAttemptAnswerRepository learningAttemptAnswerRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ObjectMapper objectMapper;

    public LearningService(LearningQuizRepository learningQuizRepository,
                           LearningQuestionRepository learningQuestionRepository,
                           LearningAttemptRepository learningAttemptRepository,
                           LearningAttemptAnswerRepository learningAttemptAnswerRepository,
                           ReviewScheduleRepository reviewScheduleRepository,
                           SourceDocumentRepository sourceDocumentRepository,
                           ObjectMapper objectMapper) {
        this.learningQuizRepository = learningQuizRepository;
        this.learningQuestionRepository = learningQuestionRepository;
        this.learningAttemptRepository = learningAttemptRepository;
        this.learningAttemptAnswerRepository = learningAttemptAnswerRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuizResponse storeGeneratedQuiz(String topic, Long sourceId, QuizResponse response) {
        SourceDocument sourceDocument = sourceId == null ? null : sourceDocumentRepository.findById(sourceId)
                .orElseThrow(() -> new java.util.NoSuchElementException("학습 자료를 찾을 수 없습니다."));
        LearningQuiz quiz = learningQuizRepository.save(new LearningQuiz(sourceDocument, topic, response.quizTitle()));

        for (int index = 0; index < response.questions().size(); index++) {
            Question question = response.questions().get(index);
            learningQuestionRepository.save(new LearningQuestion(
                    quiz,
                    index + 1,
                    question.questionText(),
                    writeJson(question.options()),
                    question.correctAnswer(),
                    question.explanation(),
                    writeJson(question.sourceReferences())
            ));
        }
        return response.withQuizId(quiz.getId());
    }

    @Transactional
    public AttemptResult recordAttempt(AttemptRequest request) {
        LearningQuiz quiz = learningQuizRepository.findById(request.quizId())
                .orElseThrow(() -> new java.util.NoSuchElementException("퀴즈를 찾을 수 없습니다."));
        List<LearningQuestion> questions = learningQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quiz.getId());
        Map<Integer, AttemptAnswerRequest> answersByOrder = answersByQuestionOrder(request.answers());

        if (questions.isEmpty() || answersByOrder.size() != questions.size()
                || questions.stream().anyMatch(question -> !answersByOrder.containsKey(question.getQuestionOrder()))) {
            throw new IllegalArgumentException("모든 문항에 답안을 제출해주세요.");
        }

        int correctAnswers = (int) questions.stream()
                .filter(question -> isCorrect(question, answersByOrder.get(question.getQuestionOrder()).selectedAnswer()))
                .count();
        LearningAttempt attempt = learningAttemptRepository.save(new LearningAttempt(quiz, questions.size(), correctAnswers));

        int reviewScheduledCount = 0;
        LocalDateTime nextReviewAt = null;
        for (LearningQuestion question : questions) {
            String selectedAnswer = answersByOrder.get(question.getQuestionOrder()).selectedAnswer().trim();
            boolean correct = isCorrect(question, selectedAnswer);
            learningAttemptAnswerRepository.save(new LearningAttemptAnswer(attempt, question, selectedAnswer, correct));
            if (!correct) {
                LocalDateTime reviewAt = LocalDateTime.now().plusDays(1);
                reviewScheduleRepository.save(new ReviewSchedule(question, reviewAt));
                reviewScheduledCount++;
                nextReviewAt = reviewAt;
            }
        }

        return new AttemptResult(
                attempt.getId(),
                questions.size(),
                correctAnswers,
                questions.size() - correctAnswers,
                reviewScheduledCount,
                nextReviewAt
        );
    }

    private Map<Integer, AttemptAnswerRequest> answersByQuestionOrder(List<AttemptAnswerRequest> answers) {
        Map<Integer, AttemptAnswerRequest> result = new HashMap<>();
        for (AttemptAnswerRequest answer : answers) {
            if (result.put(answer.questionOrder(), answer) != null) {
                throw new IllegalArgumentException("같은 문항의 답안을 두 번 제출할 수 없습니다.");
            }
        }
        return result;
    }

    private boolean isCorrect(LearningQuestion question, String selectedAnswer) {
        return question.getCorrectAnswer().trim().equals(selectedAnswer.trim());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("학습 데이터를 저장할 수 없습니다.", e);
        }
    }
}
