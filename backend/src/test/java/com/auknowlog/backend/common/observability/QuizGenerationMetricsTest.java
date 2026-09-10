package com.auknowlog.backend.common.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGenerationMetricsTest {

    @Test
    void recordsBoundedPipelineOutcomesWithoutRequestSpecificTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QuizGenerationMetrics metrics = new QuizGenerationMetrics(registry);

        metrics.recordQuestions("requested", 5);
        metrics.recordQuestions("generated", 7);
        metrics.recordQuestions("semantic_duplicate", 2);
        metrics.recordQuestions("accepted", 5);
        metrics.recordSemanticCheck("available");
        metrics.recordGeneration("success", 2, Duration.ofSeconds(3));

        assertThat(registry.get(QuizGenerationMetrics.QUESTIONS)
                .tag("outcome", "requested").counter().count()).isEqualTo(5);
        assertThat(registry.get(QuizGenerationMetrics.QUESTIONS)
                .tag("outcome", "semantic_duplicate").counter().count()).isEqualTo(2);
        assertThat(registry.get(QuizGenerationMetrics.SEMANTIC_CHECKS)
                .tag("outcome", "available").counter().count()).isEqualTo(1);
        assertThat(registry.get(QuizGenerationMetrics.GENERATION_DURATION)
                .tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get(QuizGenerationMetrics.GENERATION_ATTEMPTS)
                .tag("outcome", "success").summary().totalAmount()).isEqualTo(2);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .allSatisfy(tag -> assertThat(tag.getKey())
                        .isNotIn("topic", "question", "quiz_id", "request_id", "document_id")));
    }

    @Test
    void mapsUnexpectedValuesToOneBoundedUnknownSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QuizGenerationMetrics metrics = new QuizGenerationMetrics(registry);

        metrics.recordQuestions("value-from-user", 1);
        metrics.recordSemanticCheck("value-from-user");
        metrics.recordGeneration("value-from-user", 1, Duration.ofMillis(10));

        assertThat(registry.get(QuizGenerationMetrics.QUESTIONS)
                .tag("outcome", "unknown").counter().count()).isEqualTo(1);
        assertThat(registry.get(QuizGenerationMetrics.SEMANTIC_CHECKS)
                .tag("outcome", "unknown").counter().count()).isEqualTo(1);
        assertThat(registry.get(QuizGenerationMetrics.GENERATION_DURATION)
                .tag("outcome", "unknown").timer().count()).isEqualTo(1);
    }
}
