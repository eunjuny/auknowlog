package com.auknowlog.backend.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 퀴즈 생성 파이프라인의 결과를 낮은 카디널리티 지표로 기록한다.
 *
 * 주제, 질문 본문, quiz ID처럼 값이 계속 늘어나는 정보는 태그에 포함하지 않는다.
 * 개별 요청 추적은 DB 원장과 trace가 담당하고, 이 지표는 전체 추세와 이상 감지에 사용한다.
 */
@Component
public class QuizGenerationMetrics {

    static final String GENERATION_DURATION = "auknowlog.quiz.generation.duration";
    static final String GENERATION_ATTEMPTS = "auknowlog.quiz.generation.attempts";
    static final String QUESTIONS = "auknowlog.quiz.questions";
    static final String SEMANTIC_CHECKS = "auknowlog.quiz.semantic.checks";

    private static final Set<String> GENERATION_OUTCOMES = Set.of("success", "partial", "failure");
    private static final Set<String> QUESTION_OUTCOMES = Set.of(
            "requested", "generated", "accepted", "exact_duplicate", "semantic_duplicate",
            "feedback_duplicate", "save_conflict", "unused"
    );
    private static final Set<String> SEMANTIC_OUTCOMES = Set.of("available", "unavailable", "error", "index_error");

    private final MeterRegistry meterRegistry;

    public QuizGenerationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        GENERATION_OUTCOMES.forEach(outcome -> {
            generationTimer(outcome);
            attemptsSummary(outcome);
        });
        QUESTION_OUTCOMES.forEach(this::questionCounter);
        SEMANTIC_OUTCOMES.forEach(this::semanticCounter);
    }

    public void recordGeneration(String outcome, int attempts, Duration duration) {
        String safeOutcome = bounded(outcome, GENERATION_OUTCOMES);
        generationTimer(safeOutcome).record(duration);
        attemptsSummary(safeOutcome).record(Math.max(attempts, 0));
    }

    public void recordQuestions(String outcome, int count) {
        if (count <= 0) {
            return;
        }
        questionCounter(bounded(outcome, QUESTION_OUTCOMES)).increment(count);
    }

    public void recordSemanticCheck(String outcome) {
        semanticCounter(bounded(outcome, SEMANTIC_OUTCOMES)).increment();
    }

    private Timer generationTimer(String outcome) {
        return Timer.builder(GENERATION_DURATION)
                .description("End-to-end duration of the quiz generation pipeline")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private DistributionSummary attemptsSummary(String outcome) {
        return DistributionSummary.builder(GENERATION_ATTEMPTS)
                .baseUnit("attempts")
                .description("Number of model generation attempts per quiz request")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private Counter questionCounter(String outcome) {
        return Counter.builder(QUESTIONS)
                .baseUnit("questions")
                .description("Quiz questions by pipeline outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private Counter semanticCounter(String outcome) {
        return Counter.builder(SEMANTIC_CHECKS)
                .description("Semantic duplicate protection checks by outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private String bounded(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : "unknown";
    }
}
