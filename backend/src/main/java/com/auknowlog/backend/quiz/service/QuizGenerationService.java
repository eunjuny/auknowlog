package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.common.exception.OpenAiUnavailableException;
import com.auknowlog.backend.embedding.service.SemanticDuplicateService;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.source.dto.SourceChunkContext;
import com.auknowlog.backend.source.service.SourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 퀴즈 생성 유스케이스를 조립한다. HTTP 계층은 요청 검증과 응답만 맡기고,
 * 생성, 중복 차단, 이력 저장, 검색 색인의 순서를 이 서비스가 보장한다.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private final OpenAiQuizService openAiQuizService;
    private final QuestionHistoryService questionHistoryService;
    private final SemanticDuplicateService semanticDuplicateService;
    private final SourceService sourceService;
    private final LearningService learningService;
    private final LangfuseTracingService langfuseTracingService;

    public QuizGenerationService(OpenAiQuizService openAiQuizService,
                                 QuestionHistoryService questionHistoryService,
                                 SemanticDuplicateService semanticDuplicateService,
                                 SourceService sourceService,
                                 LearningService learningService,
                                 LangfuseTracingService langfuseTracingService) {
        this.openAiQuizService = openAiQuizService;
        this.questionHistoryService = questionHistoryService;
        this.semanticDuplicateService = semanticDuplicateService;
        this.sourceService = sourceService;
        this.learningService = learningService;
        this.langfuseTracingService = langfuseTracingService;
    }

    public QuizResponse createQuiz(QuizRequest request) {
        int targetCount = request.numberOfQuestions() == null ? 5 : request.numberOfQuestions();
        String topic = request.topic().trim();

        try (LangfuseTracingService.TraceScope trace = langfuseTracingService
                .startQuizGeneration(topic, targetCount, request.sourceId() != null)) {
            try {
                List<Question> collectedQuestions = new ArrayList<>();
                String quizTitle = topic + " 퀴즈";
                int attempts = 0;
                List<SourceChunkContext> sourceContext;
                try (LangfuseTracingService.TraceScope sourceLookup = langfuseTracingService.startOperation(
                        "source-context-lookup", Map.of("sourceProvided", request.sourceId() != null)
                )) {
                    sourceContext = request.sourceId() == null
                            ? List.of()
                            : sourceService.getQuizContext(request.sourceId());
                    sourceLookup.complete(Map.of("sourceChunkCount", sourceContext.size()));
                }

                while (collectedQuestions.size() < targetCount && attempts < MAX_GENERATION_ATTEMPTS) {
                    int remaining = targetCount - collectedQuestions.size();
                    int requestedCount = attempts == 0 ? remaining : Math.min(remaining * 2, 20);

                    log.info("Quiz generation attempt {}/{}: need {}, request {}",
                            attempts + 1, MAX_GENERATION_ATTEMPTS, remaining, requestedCount);

                    try {
                        List<String> existingQuestions;
                        try (LangfuseTracingService.TraceScope historyLookup = langfuseTracingService.startOperation(
                                "recent-question-lookup", Map.of("attempt", attempts + 1, "limit", attempts == 0 ? 30 : 0)
                        )) {
                            existingQuestions = attempts == 0
                                    ? questionHistoryService.getRecentQuestionPreviews(topic, 30)
                                    : List.of();
                            historyLookup.complete(Map.of("candidateCount", existingQuestions.size()));
                        }

                        QuizResponse response = openAiQuizService.generateQuiz(topic, requestedCount, existingQuestions, sourceContext);
                        if (attempts == 0) {
                            quizTitle = response.quizTitle();
                        }

                        for (Question question : response.questions()) {
                            if (collectedQuestions.size() >= targetCount) {
                                break;
                            }
                            boolean exactDuplicate;
                            try (LangfuseTracingService.TraceScope exactCheck = langfuseTracingService.startOperation(
                                    "exact-duplicate-check", Map.of()
                            )) {
                                exactDuplicate = questionHistoryService.isDuplicate(question.questionText());
                                exactCheck.complete(Map.of("duplicate", exactDuplicate));
                            }
                            if (exactDuplicate) {
                                log.info("Exact duplicate question filtered: {}", preview(question.questionText()));
                                continue;
                            }

                            SemanticDuplicateService.SemanticCheck semanticCheck;
                            try (LangfuseTracingService.TraceScope semanticCheckTrace = langfuseTracingService.startOperation(
                                    "semantic-duplicate-check", Map.of("threshold", 0.90)
                            )) {
                                try {
                                    semanticCheck = semanticDuplicateService.check(question.questionText());
                                    semanticCheckTrace.complete(Map.of(
                                            "available", semanticCheck.embedding().isPresent(),
                                            "duplicate", semanticCheck.duplicate(),
                                            "similarity", semanticCheck.similarity()
                                    ));
                                } catch (Exception e) {
                                    // 임베딩은 선택 기능이다. 장애 시 이미 검증된 정확 중복 제약만 적용해 생성 요청을 완료한다.
                                    semanticCheckTrace.fail(e);
                                    log.warn("Semantic duplicate check failed; using exact duplicate protection only", e);
                                    semanticCheck = SemanticDuplicateService.SemanticCheck.notAvailable();
                                }
                            }
                            if (semanticCheck.duplicate()) {
                                log.info("Semantic duplicate question filtered: similarity={}, question={}",
                                        semanticCheck.similarity(), preview(question.questionText()));
                                continue;
                            }
                            if (questionHistoryService.saveQuestion(topic, question)) {
                                try {
                                    semanticDuplicateService.indexSavedQuestion(question.questionText(), semanticCheck);
                                } catch (Exception e) {
                                    log.warn("Semantic index update failed; exact duplicate protection remains active", e);
                                }
                                collectedQuestions.add(question);
                            }
                        }
                    } catch (OpenAiUnavailableException | IllegalStateException e) {
                        throw e;
                    } catch (Exception e) {
                        log.warn("Quiz generation attempt {} failed", attempts + 1, e);
                    }
                    attempts++;
                }

                log.info("Quiz generation completed: requested {}, generated {}, attempts {}",
                        targetCount, collectedQuestions.size(), attempts);
                QuizResponse quiz = learningService.storeGeneratedQuiz(topic, request.sourceId(), request.roadmapId(),
                        request.roadmapStepId(), new QuizResponse(quizTitle, collectedQuestions));
                trace.complete(Map.of(
                        "generatedQuestionCount", collectedQuestions.size(),
                        "generationAttempts", attempts
                ));
                return quiz;
            } catch (RuntimeException e) {
                trace.fail(e);
                throw e;
            }
        }
    }

    private String preview(String question) {
        return question.substring(0, Math.min(50, question.length()));
    }
}
