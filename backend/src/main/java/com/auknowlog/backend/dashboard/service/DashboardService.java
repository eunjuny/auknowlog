package com.auknowlog.backend.dashboard.service;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import com.auknowlog.backend.ai.dto.AiBudgetSnapshot;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import com.auknowlog.backend.ai.service.AiUsagePolicyService;
import com.auknowlog.backend.dashboard.dto.AiDashboardSummary;
import com.auknowlog.backend.dashboard.dto.DailyAiMetric;
import com.auknowlog.backend.dashboard.dto.DailyLearningMetric;
import com.auknowlog.backend.dashboard.dto.DashboardSummary;
import com.auknowlog.backend.dashboard.dto.LearningDashboardSummary;
import com.auknowlog.backend.dashboard.dto.LearningRecommendation;
import com.auknowlog.backend.dashboard.dto.ModelAiMetric;
import com.auknowlog.backend.dashboard.dto.QualityFeedbackDashboardSummary;
import com.auknowlog.backend.dashboard.dto.QuestionFeedbackTypeMetric;
import com.auknowlog.backend.dashboard.dto.TopicLearningMetric;
import com.auknowlog.backend.feedback.entity.QuestionFeedback;
import com.auknowlog.backend.feedback.repository.QuestionFeedbackRepository;
import com.auknowlog.backend.learning.entity.LearningAttempt;
import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.learning.repository.ReviewScheduleRepository;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final int ACTIVITY_DAYS = 14;
    private static final int WEAK_ACCURACY_PERCENT = 70;
    private static final int REINFORCE_ACCURACY_PERCENT = 85;
    private static final int STALE_REVIEW_DAYS = 7;

    private final LearningAttemptRepository learningAttemptRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final AiGenerationLogRepository aiGenerationLogRepository;
    private final AiUsagePolicyService aiUsagePolicyService;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final QuestionFeedbackRepository questionFeedbackRepository;

    public DashboardService(LearningAttemptRepository learningAttemptRepository,
                            ReviewScheduleRepository reviewScheduleRepository,
                            AiGenerationLogRepository aiGenerationLogRepository,
                            AiUsagePolicyService aiUsagePolicyService,
                            QuestionHistoryRepository questionHistoryRepository,
                            QuestionFeedbackRepository questionFeedbackRepository) {
        this.learningAttemptRepository = learningAttemptRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.aiGenerationLogRepository = aiGenerationLogRepository;
        this.aiUsagePolicyService = aiUsagePolicyService;
        this.questionHistoryRepository = questionHistoryRepository;
        this.questionFeedbackRepository = questionFeedbackRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        LocalDate today = LocalDate.now();
        LocalDate firstActivityDate = today.minusDays(ACTIVITY_DAYS - 1L);
        LocalDateTime activityStart = firstActivityDate.atStartOfDay();

        List<LearningAttempt> allAttempts = learningAttemptRepository.findAllWithQuiz();
        List<AiGenerationLog> recentAiLogs = aiGenerationLogRepository.findByCreatedAtGreaterThanEqual(activityStart);

        return new DashboardSummary(
                today,
                buildLearningSummary(allAttempts, firstActivityDate),
                buildAiSummary(recentAiLogs, firstActivityDate),
                buildQualityFeedbackSummary()
        );
    }

    private LearningDashboardSummary buildLearningSummary(List<LearningAttempt> attempts, LocalDate firstActivityDate) {
        long totalQuestions = attempts.stream().mapToLong(LearningAttempt::getTotalQuestions).sum();
        long correctAnswers = attempts.stream().mapToLong(LearningAttempt::getCorrectAnswers).sum();
        Map<LocalDate, LongAccumulator> daily = emptyDailyAccumulators(firstActivityDate);
        Map<String, LongAccumulator> topics = new HashMap<>();

        for (LearningAttempt attempt : attempts) {
            LocalDate submittedDate = attempt.getSubmittedAt().toLocalDate();
            if (!submittedDate.isBefore(firstActivityDate)) {
                LongAccumulator day = daily.get(submittedDate);
                if (day != null) {
                    day.addAttempt(attempt.getCorrectAnswers(), attempt.getTotalQuestions());
                }
            }
            String topic = attempt.getQuiz().getTopic();
            topics.computeIfAbsent(topic, ignored -> new LongAccumulator())
                    .addTopicAttempt(attempt.getCorrectAnswers(), attempt.getTotalQuestions(), attempt.getSubmittedAt());
        }

        List<DailyLearningMetric> activity = daily.entrySet().stream()
                .map(entry -> new DailyLearningMetric(
                        entry.getKey(),
                        entry.getValue().attempts,
                        entry.getValue().correctAnswers,
                        entry.getValue().totalQuestions
                ))
                .toList();
        List<TopicLearningMetric> topicAccuracy = topics.entrySet().stream()
                .map(entry -> new TopicLearningMetric(
                        entry.getKey(),
                        entry.getValue().attempts,
                        entry.getValue().correctAnswers,
                        entry.getValue().totalQuestions,
                        percentage(entry.getValue().correctAnswers, entry.getValue().totalQuestions)
                ))
                .sorted(Comparator.comparingLong(TopicLearningMetric::attempts).reversed()
                        .thenComparing(TopicLearningMetric::topic))
                .limit(8)
                .toList();

        return new LearningDashboardSummary(
                attempts.size(),
                totalQuestions,
                correctAnswers,
                percentage(correctAnswers, totalQuestions),
                reviewScheduleRepository.countByStatusAndNextReviewAtLessThanEqual("PENDING", LocalDateTime.now()),
                activity,
                topicAccuracy,
                buildRecommendations(topics)
        );
    }

    private List<LearningRecommendation> buildRecommendations(Map<String, LongAccumulator> topics) {
        LocalDateTime now = LocalDateTime.now();

        return topics.entrySet().stream()
                .map(entry -> recommendationFor(entry.getKey(), entry.getValue(), now))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(RecommendationCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.recommendation().topic()))
                .limit(3)
                .map(RecommendationCandidate::recommendation)
                .toList();
    }

    private RecommendationCandidate recommendationFor(String topic, LongAccumulator metric, LocalDateTime now) {
        int accuracy = percentage(metric.correctAnswers, metric.totalQuestions);
        long daysSinceStudy = metric.lastStudiedAt == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(metric.lastStudiedAt.toLocalDate(), now.toLocalDate()));
        int questionCount = recommendedQuestionCount(accuracy);

        if (accuracy < WEAK_ACCURACY_PERCENT) {
            int score = 1_000 + (WEAK_ACCURACY_PERCENT - accuracy) * 10 + (int) Math.min(metric.totalQuestions, 20);
            return new RecommendationCandidate(new LearningRecommendation(
                    topic,
                    "WEAKNESS",
                    "우선 보완이 필요한 주제",
                    "누적 정답률이 " + accuracy + "%입니다. 기초 문제 " + questionCount + "개로 핵심 개념을 다시 점검해보세요.",
                    accuracy,
                    metric.totalQuestions,
                    metric.lastStudiedAt,
                    questionCount
            ), score);
        }

        if (accuracy < REINFORCE_ACCURACY_PERCENT) {
            int score = 500 + (REINFORCE_ACCURACY_PERCENT - accuracy) * 10 + (int) Math.min(metric.totalQuestions, 20);
            return new RecommendationCandidate(new LearningRecommendation(
                    topic,
                    "REINFORCE",
                    "한 번 더 연습하면 좋은 주제",
                    "누적 정답률이 " + accuracy + "%입니다. 난이도를 유지한 문제 " + questionCount + "개로 이해를 굳혀보세요.",
                    accuracy,
                    metric.totalQuestions,
                    metric.lastStudiedAt,
                    questionCount
            ), score);
        }

        if (daysSinceStudy >= STALE_REVIEW_DAYS) {
            int score = 100 + (int) Math.min(daysSinceStudy, 60);
            return new RecommendationCandidate(new LearningRecommendation(
                    topic,
                    "REVIEW",
                    "기억 유지를 위한 복습 주제",
                    daysSinceStudy + "일 동안 이 주제를 풀지 않았습니다. 짧은 복습 문제 " + questionCount + "개를 풀어보세요.",
                    accuracy,
                    metric.totalQuestions,
                    metric.lastStudiedAt,
                    questionCount
            ), score);
        }

        return null;
    }

    private int recommendedQuestionCount(int accuracyPercent) {
        if (accuracyPercent < 50) {
            return 5;
        }
        if (accuracyPercent < WEAK_ACCURACY_PERCENT) {
            return 4;
        }
        if (accuracyPercent < REINFORCE_ACCURACY_PERCENT) {
            return 3;
        }
        return 2;
    }

    private AiDashboardSummary buildAiSummary(List<AiGenerationLog> logs, LocalDate firstActivityDate) {
        AiBudgetSnapshot budget = aiUsagePolicyService.snapshot();
        long successfulCalls = logs.stream().filter(this::isSuccess).count();
        long totalTokens = logs.stream().mapToLong(log -> tokens(log.getTotalTokens())).sum();
        long averageLatency = logs.isEmpty() ? 0 : Math.round(logs.stream().mapToLong(AiGenerationLog::getLatencyMs).average().orElse(0));
        Map<LocalDate, LongAccumulator> daily = emptyDailyAccumulators(firstActivityDate);
        Map<String, LongAccumulator> models = new HashMap<>();

        for (AiGenerationLog log : logs) {
            LongAccumulator day = daily.get(log.getCreatedAt().toLocalDate());
            if (day != null) {
                day.addAiCall(tokens(log.getTotalTokens()), !isSuccess(log));
            }
            models.computeIfAbsent(normalizedModel(log.getModel()), ignored -> new LongAccumulator())
                    .addModelCall(tokens(log.getTotalTokens()), log.getLatencyMs(), isSuccess(log));
        }

        List<DailyAiMetric> activity = daily.entrySet().stream()
                .map(entry -> new DailyAiMetric(
                        entry.getKey(),
                        entry.getValue().calls,
                        entry.getValue().totalTokens,
                        entry.getValue().failedCalls
                ))
                .toList();
        List<ModelAiMetric> modelUsage = models.entrySet().stream()
                .map(entry -> new ModelAiMetric(
                        entry.getKey(),
                        entry.getValue().calls,
                        entry.getValue().totalTokens,
                        percentage(entry.getValue().successfulCalls, entry.getValue().calls),
                        entry.getValue().calls == 0 ? 0 : Math.round((double) entry.getValue().latencySumMs / entry.getValue().calls)
                ))
                .sorted(Comparator.comparingLong(ModelAiMetric::calls).reversed()
                        .thenComparing(ModelAiMetric::model))
                .toList();

        return new AiDashboardSummary(
                logs.size(),
                successfulCalls,
                logs.size() - successfulCalls,
                totalTokens,
                percentage(successfulCalls, logs.size()),
                averageLatency,
                percentile95(logs.stream().map(AiGenerationLog::getLatencyMs).toList()),
                questionHistoryRepository.count(),
                budget.enforcementEnabled(),
                budget.dailyTokenBudget(),
                budget.todayTokens(),
                budget.remainingTokens(),
                budget.usedPercent(),
                activity,
                modelUsage
        );
    }

    private QualityFeedbackDashboardSummary buildQualityFeedbackSummary() {
        Map<String, Long> feedbackTypes = new HashMap<>();
        for (QuestionFeedback feedback : questionFeedbackRepository.findAll()) {
            feedbackTypes.merge(feedback.getFeedbackType().name(), 1L, Long::sum);
        }

        List<QuestionFeedbackTypeMetric> typeMetrics = feedbackTypes.entrySet().stream()
                .map(entry -> {
                    var type = com.auknowlog.backend.feedback.entity.QuestionFeedbackType.valueOf(entry.getKey());
                    return new QuestionFeedbackTypeMetric(type.name(), type.getDisplayName(), entry.getValue());
                })
                .sorted(Comparator.comparingLong(QuestionFeedbackTypeMetric::count).reversed()
                        .thenComparing(QuestionFeedbackTypeMetric::type))
                .toList();

        return new QualityFeedbackDashboardSummary(
                questionFeedbackRepository.count(),
                questionFeedbackRepository.countByStatus("OPEN"),
                typeMetrics
        );
    }

    private Map<LocalDate, LongAccumulator> emptyDailyAccumulators(LocalDate firstActivityDate) {
        Map<LocalDate, LongAccumulator> daily = new java.util.LinkedHashMap<>();
        for (int day = 0; day < ACTIVITY_DAYS; day++) {
            daily.put(firstActivityDate.plusDays(day), new LongAccumulator());
        }
        return daily;
    }

    private boolean isSuccess(AiGenerationLog log) {
        return "SUCCESS".equals(log.getStatus());
    }

    private long tokens(Long totalTokens) {
        return totalTokens == null ? 0 : totalTokens;
    }

    private String normalizedModel(String model) {
        return model == null || model.isBlank() ? "unknown" : model;
    }

    private int percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : (int) Math.round((double) numerator * 100 / denominator);
    }

    private long percentile95(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(index);
    }

    private static final class LongAccumulator {
        private long attempts;
        private long correctAnswers;
        private long totalQuestions;
        private long calls;
        private long successfulCalls;
        private long failedCalls;
        private long totalTokens;
        private long latencySumMs;
        private LocalDateTime lastStudiedAt;

        private void addAttempt(long correct, long total) {
            attempts++;
            correctAnswers += correct;
            totalQuestions += total;
        }

        private void addTopicAttempt(long correct, long total, LocalDateTime submittedAt) {
            addAttempt(correct, total);
            if (lastStudiedAt == null || submittedAt.isAfter(lastStudiedAt)) {
                lastStudiedAt = submittedAt;
            }
        }

        private void addAiCall(long tokens, boolean failed) {
            calls++;
            totalTokens += tokens;
            if (failed) {
                failedCalls++;
            }
        }

        private void addModelCall(long tokens, long latencyMs, boolean success) {
            calls++;
            totalTokens += tokens;
            latencySumMs += latencyMs;
            if (success) {
                successfulCalls++;
            }
        }
    }

    private record RecommendationCandidate(LearningRecommendation recommendation, int score) {
    }
}
