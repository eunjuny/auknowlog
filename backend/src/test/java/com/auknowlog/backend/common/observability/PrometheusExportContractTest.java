package com.auknowlog.backend.common.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusExportContractTest {

    @Test
    void exportsAiCostAndQuizQualityMetricsWithoutHighCardinalityLabels() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.config().commonTags("application", "auknowlog");

        QuizGenerationMetrics quizMetrics = new QuizGenerationMetrics(registry);
        AiGenerationMetrics aiMetrics = new AiGenerationMetrics(registry);

        quizMetrics.recordQuestions("requested", 5);
        quizMetrics.recordQuestions("accepted", 3);
        quizMetrics.recordQuestions("semantic_duplicate", 2);
        quizMetrics.recordSemanticCheck("available");
        quizMetrics.recordGeneration("success", 2, Duration.ofSeconds(3));
        aiMetrics.recordSuccess(
                "quiz",
                "gpt-5.4-mini",
                new ObjectMapper().readTree("""
                        {
                          "input_tokens": 120,
                          "output_tokens": 80,
                          "total_tokens": 200,
                          "input_tokens_details": {"cached_tokens": 20},
                          "output_tokens_details": {"reasoning_tokens": 15}
                        }
                        """),
                Duration.ofMillis(900)
        );

        String scrape = registry.scrape();

        assertThat(scrape)
                .contains("# TYPE auknowlog_quiz_questions_total counter")
                .contains("auknowlog_quiz_questions_total{application=\"auknowlog\",outcome=\"semantic_duplicate\"} 2.0")
                .contains("# TYPE auknowlog_quiz_generation_duration_seconds summary")
                .contains("# TYPE auknowlog_ai_request_duration_seconds summary")
                .contains("auknowlog_ai_tokens_sum{application=\"auknowlog\",model=\"gpt-5.4-mini\",operation=\"quiz\",type=\"total\"} 200.0")
                .doesNotContain("topic=")
                .doesNotContain("question=")
                .doesNotContain("quiz_id=")
                .doesNotContain("request_id=")
                .doesNotContain("document_id=");
    }
}
