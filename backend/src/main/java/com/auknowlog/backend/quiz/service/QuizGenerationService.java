package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.common.exception.OpenAiUnavailableException;
import com.auknowlog.backend.common.observability.QuizGenerationMetrics;
import com.auknowlog.backend.embedding.service.SemanticDuplicateService;
import com.auknowlog.backend.feedback.service.QuestionFeedbackService;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizObjectiveAllocation;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.quiz.dto.RoadmapQuizPlan;
import com.auknowlog.backend.roadmap.service.RoadmapQuizPlanningService;
import com.auknowlog.backend.source.dto.SourceChunkContext;
import com.auknowlog.backend.source.service.SourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 퀴즈 생성 유스케이스를 조립한다. HTTP 계층은 요청 검증과 응답만 맡기고,
 * 생성, 중복 차단, 이력 저장, 검색 색인의 순서를 이 서비스가 보장한다.
 */
@Service
public class QuizGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final int MAX_PROMPT_AVOIDANCE_QUESTIONS = 30;
    private static final int MAX_FEEDBACK_AVOIDANCE_QUESTIONS = 10;
    private static final double FEEDBACK_DUPLICATE_THRESHOLD = 0.82;

    private final OpenAiQuizService openAiQuizService;
    private final QuestionHistoryService questionHistoryService;
    private final SemanticDuplicateService semanticDuplicateService;
    private final QuestionFeedbackService questionFeedbackService;
    private final SourceService sourceService;
    private final LearningService learningService;
    private final RoadmapQuizPlanningService roadmapQuizPlanningService;
    private final LangfuseTracingService langfuseTracingService;
    private final QuizGenerationMetrics quizGenerationMetrics;

    public QuizGenerationService(OpenAiQuizService openAiQuizService,
                                 QuestionHistoryService questionHistoryService,
                                 SemanticDuplicateService semanticDuplicateService,
                                 QuestionFeedbackService questionFeedbackService,
                                 SourceService sourceService,
                                 LearningService learningService,
                                 RoadmapQuizPlanningService roadmapQuizPlanningService,
                                 LangfuseTracingService langfuseTracingService,
                                 QuizGenerationMetrics quizGenerationMetrics) {
        this.openAiQuizService = openAiQuizService;
        this.questionHistoryService = questionHistoryService;
        this.semanticDuplicateService = semanticDuplicateService;
        this.questionFeedbackService = questionFeedbackService;
        this.sourceService = sourceService;
        this.learningService = learningService;
        this.roadmapQuizPlanningService = roadmapQuizPlanningService;
        this.langfuseTracingService = langfuseTracingService;
        this.quizGenerationMetrics = quizGenerationMetrics;
    }

    public QuizResponse createQuiz(QuizRequest request) {
        RoadmapQuizPlan roadmapPlan = roadmapQuizPlanningService.plan(request);
        int targetCount = roadmapPlan.questionCount();
        String topic = request.topic().trim();
        int attempts = 0;
        long startedAt = System.nanoTime();
        quizGenerationMetrics.recordQuestions("requested", targetCount);

        try (LangfuseTracingService.TraceScope trace = langfuseTracingService
                .startQuizGeneration(topic, targetCount, request.sourceId() != null)) {
            try {
                List<Question> collectedQuestions = new ArrayList<>();
                String quizTitle = topic + " 퀴즈";
                List<SourceChunkContext> sourceContext;
                try (LangfuseTracingService.TraceScope sourceLookup = langfuseTracingService.startOperation(
                        "source-context-lookup", Map.of("sourceProvided", request.sourceId() != null)
                )) {
                    sourceContext = roadmapPlan.sourceId() == null
                            ? List.of()
                            : sourceService.getQuizContext(roadmapPlan.sourceId());
                    sourceLookup.complete(Map.of("sourceChunkCount", sourceContext.size()));
                }
                List<String> feedbackAvoidanceQuestions;
                try (LangfuseTracingService.TraceScope feedbackLookup = langfuseTracingService.startOperation(
                        "similarity-feedback-lookup", Map.of("limit", MAX_FEEDBACK_AVOIDANCE_QUESTIONS)
                )) {
                    feedbackAvoidanceQuestions = questionFeedbackService.getSimilarityAvoidanceQuestions(
                            topic, MAX_FEEDBACK_AVOIDANCE_QUESTIONS);
                    feedbackLookup.complete(Map.of("candidateCount", feedbackAvoidanceQuestions.size()));
                }
                List<String> feedbackAvoidanceHashes = feedbackAvoidanceQuestions.stream()
                        .map(questionHistoryService::generateHash)
                        .distinct()
                        .toList();

                while (collectedQuestions.size() < targetCount && attempts < MAX_GENERATION_ATTEMPTS) {
                    int remaining = targetCount - collectedQuestions.size();
                    List<QuizObjectiveAllocation> objectiveAllocations = remainingObjectiveAllocations(
                            roadmapPlan.objectiveAllocations(), collectedQuestions);
                    int requestedCount = roadmapPlan.objectiveBased()
                            ? objectiveAllocations.stream().mapToInt(QuizObjectiveAllocation::questionCount).sum()
                            : attempts == 0 ? remaining : Math.min(remaining * 2, 20);

                    log.info("Quiz generation attempt {}/{}: need {}, request {}",
                            attempts + 1, MAX_GENERATION_ATTEMPTS, remaining, requestedCount);

                    try {
                        List<String> existingQuestions;
                        try (LangfuseTracingService.TraceScope historyLookup = langfuseTracingService.startOperation(
                                "recent-question-lookup", Map.of("attempt", attempts + 1, "limit", attempts == 0 ? 30 : 0)
                        )) {
                            List<String> recentQuestions = attempts == 0
                                    ? questionHistoryService.getRecentQuestionPreviews(topic, MAX_PROMPT_AVOIDANCE_QUESTIONS)
                                    : List.of();
                            existingQuestions = attempts == 0
                                    ? mergePromptAvoidanceQuestions(feedbackAvoidanceQuestions, recentQuestions)
                                    : List.of();
                            historyLookup.complete(Map.of(
                                    "candidateCount", existingQuestions.size(),
                                    "feedbackCandidateCount", feedbackAvoidanceQuestions.size()
                            ));
                        }

                        QuizResponse response = roadmapPlan.objectiveBased()
                                ? openAiQuizService.generateQuiz(
                                        topic, requestedCount, existingQuestions, sourceContext, objectiveAllocations)
                                : openAiQuizService.generateQuiz(topic, requestedCount, existingQuestions, sourceContext);
                        quizGenerationMetrics.recordQuestions("generated", response.questions().size());
                        if (attempts == 0) {
                            quizTitle = response.quizTitle();
                        }

                        int inspectedCandidates = 0;
                        for (Question question : response.questions()) {
                            if (collectedQuestions.size() >= targetCount) {
                                break;
                            }
                            inspectedCandidates++;
                            boolean exactDuplicate;
                            try (LangfuseTracingService.TraceScope exactCheck = langfuseTracingService.startOperation(
                                    "exact-duplicate-check", Map.of()
                            )) {
                                exactDuplicate = questionHistoryService.isDuplicate(question.questionText());
                                exactCheck.complete(Map.of("duplicate", exactDuplicate));
                            }
                            if (exactDuplicate) {
                                quizGenerationMetrics.recordQuestions("exact_duplicate", 1);
                                log.info("Exact duplicate question filtered: {}", preview(question.questionText()));
                                continue;
                            }

                            SemanticDuplicateService.SemanticCheck semanticCheck;
                            try (LangfuseTracingService.TraceScope semanticCheckTrace = langfuseTracingService.startOperation(
                                    "semantic-duplicate-check", Map.of("threshold", 0.90)
                            )) {
                                try {
                                    semanticCheck = semanticDuplicateService.check(question.questionText());
                                    quizGenerationMetrics.recordSemanticCheck(
                                            semanticCheck.embedding().isPresent() ? "available" : "unavailable");
                                    semanticCheckTrace.complete(Map.of(
                                            "available", semanticCheck.embedding().isPresent(),
                                            "duplicate", semanticCheck.duplicate(),
                                            "similarity", semanticCheck.similarity()
                                    ));
                                } catch (Exception e) {
                                    // 임베딩은 선택 기능이다. 장애 시 이미 검증된 정확 중복 제약만 적용해 생성 요청을 완료한다.
                                    quizGenerationMetrics.recordSemanticCheck("error");
                                    semanticCheckTrace.fail(e);
                                    log.warn("Semantic duplicate check failed; using exact duplicate protection only", e);
                                    semanticCheck = SemanticDuplicateService.SemanticCheck.notAvailable();
                                }
                            }
                            if (semanticCheck.duplicate()) {
                                quizGenerationMetrics.recordQuestions("semantic_duplicate", 1);
                                log.info("Semantic duplicate question filtered: similarity={}, question={}",
                                        semanticCheck.similarity(), preview(question.questionText()));
                                continue;
                            }
                            if (!feedbackAvoidanceHashes.isEmpty()) {
                                try (LangfuseTracingService.TraceScope feedbackCheckTrace = langfuseTracingService.startOperation(
                                        "similarity-feedback-check", Map.of("threshold", FEEDBACK_DUPLICATE_THRESHOLD)
                                )) {
                                    try {
                                        semanticCheck = semanticDuplicateService.checkAgainstQuestionHashes(
                                                semanticCheck, feedbackAvoidanceHashes, FEEDBACK_DUPLICATE_THRESHOLD);
                                        feedbackCheckTrace.complete(Map.of(
                                                "duplicate", semanticCheck.duplicate(),
                                                "similarity", semanticCheck.similarity()
                                        ));
                                    } catch (Exception e) {
                                        quizGenerationMetrics.recordSemanticCheck("error");
                                        feedbackCheckTrace.fail(e);
                                        log.warn("Feedback-based semantic check failed; keeping standard duplicate protection", e);
                                    }
                                }
                                if (semanticCheck.duplicate()) {
                                    quizGenerationMetrics.recordQuestions("feedback_duplicate", 1);
                                    log.info("User-feedback duplicate question filtered: similarity={}, question={}",
                                            semanticCheck.similarity(), preview(question.questionText()));
                                    continue;
                                }
                            }
                            if (questionHistoryService.saveQuestion(topic, question)) {
                                try {
                                    semanticDuplicateService.indexSavedQuestion(question.questionText(), semanticCheck);
                                } catch (Exception e) {
                                    quizGenerationMetrics.recordSemanticCheck("index_error");
                                    log.warn("Semantic index update failed; exact duplicate protection remains active", e);
                                }
                                collectedQuestions.add(question);
                                quizGenerationMetrics.recordQuestions("accepted", 1);
                            } else {
                                quizGenerationMetrics.recordQuestions("save_conflict", 1);
                            }
                        }
                        quizGenerationMetrics.recordQuestions("unused", response.questions().size() - inspectedCandidates);
                    } catch (OpenAiUnavailableException | IllegalStateException e) {
                        throw e;
                    } catch (Exception e) {
                        log.warn("Quiz generation attempt {} failed", attempts + 1, e);
                    }
                    attempts++;
                }

                log.info("Quiz generation completed: requested {}, generated {}, attempts {}",
                        targetCount, collectedQuestions.size(), attempts);
                QuizResponse quiz = learningService.storeGeneratedQuiz(topic, roadmapPlan.sourceId(), request.roadmapId(),
                        request.roadmapStepId(), new QuizResponse(quizTitle, collectedQuestions));
                trace.complete(Map.of(
                        "generatedQuestionCount", collectedQuestions.size(),
                        "generationAttempts", attempts
                ));
                quizGenerationMetrics.recordGeneration(
                        collectedQuestions.size() == targetCount ? "success" : "partial",
                        attempts,
                        Duration.ofNanos(System.nanoTime() - startedAt));
                return quiz;
            } catch (RuntimeException e) {
                trace.fail(e);
                throw e;
            }
        } catch (RuntimeException e) {
            quizGenerationMetrics.recordGeneration(
                    "failure", attempts, Duration.ofNanos(System.nanoTime() - startedAt));
            throw e;
        }
    }

    private String preview(String question) {
        return question.substring(0, Math.min(50, question.length()));
    }

    private List<QuizObjectiveAllocation> remainingObjectiveAllocations(
            List<QuizObjectiveAllocation> planned,
            List<Question> acceptedQuestions) {
        if (planned == null || planned.isEmpty()) return List.of();
        Map<String, Long> acceptedByObjective = acceptedQuestions.stream()
                .filter(question -> question.objectiveKey() != null)
                .collect(Collectors.groupingBy(Question::objectiveKey, Collectors.counting()));
        return planned.stream()
                .map(allocation -> new QuizObjectiveAllocation(
                        allocation.objectiveId(), allocation.key(), allocation.title(), allocation.description(),
                        allocation.importance(), Math.max(0, allocation.questionCount()
                        - acceptedByObjective.getOrDefault(allocation.key(), 0L).intValue())
                ))
                .filter(allocation -> allocation.questionCount() > 0)
                .toList();
    }

    private List<String> mergePromptAvoidanceQuestions(List<String> feedbackQuestions, List<String> recentQuestions) {
        List<String> merged = new ArrayList<>();
        for (String question : feedbackQuestions) {
            addPromptPreview(merged, question);
        }
        for (String question : recentQuestions) {
            addPromptPreview(merged, question);
        }
        return merged;
    }

    private void addPromptPreview(List<String> questions, String question) {
        if (questions.size() >= MAX_PROMPT_AVOIDANCE_QUESTIONS || question == null || question.isBlank()) {
            return;
        }
        String preview = question.length() > 50 ? question.substring(0, 50) + "..." : question;
        if (!questions.contains(preview)) {
            questions.add(preview);
        }
    }
}
