package com.auknowlog.backend.common.observability;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * OpenAI 호출의 성공률, 지연 시간, 토큰 사용량을 모델별로 기록한다.
 *
 * 가격은 공급자 정책에 따라 바뀌므로 애플리케이션에서 금액으로 환산하지 않고,
 * 실제 사용량만 기록해 대시보드 또는 비용 분석 도구에서 해석하도록 한다.
 */
@Component
public class AiGenerationMetrics {

    private static final String REQUEST_DURATION = "auknowlog.ai.request.duration";
    private static final String TOKEN_USAGE = "auknowlog.ai.tokens";
    private static final Set<String> OPERATIONS = Set.of("quiz", "roadmap");
    private static final Set<String> OUTCOMES = Set.of(
            "success", "unavailable", "upstream_rejected", "configuration", "invalid_response", "unexpected"
    );

    private final MeterRegistry meterRegistry;

    public AiGenerationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(String operation, String model, JsonNode usage, Duration duration) {
        recordRequest(operation, model, "success", duration);
        recordTokenUsage(operation, model, usage);
    }

    public void recordFailure(String operation, String model, String failureType, Duration duration) {
        recordRequest(operation, model, failureType, duration);
    }

    private void recordRequest(String operation, String model, String outcome, Duration duration) {
        Timer.builder(REQUEST_DURATION)
                .description("Duration of an OpenAI generation request")
                .tags(commonTags(operation, model).and("outcome", bounded(outcome, OUTCOMES)))
                .register(meterRegistry)
                .record(duration);
    }

    private void recordTokenUsage(String operation, String model, JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return;
        }

        recordToken(operation, model, "input", usage.path("input_tokens").asLong(0));
        recordToken(operation, model, "output", usage.path("output_tokens").asLong(0));
        recordToken(operation, model, "total", usage.path("total_tokens").asLong(0));
        recordToken(operation, model, "reasoning", usage.path("output_tokens_details").path("reasoning_tokens").asLong(0));
        recordToken(operation, model, "cached_input", usage.path("input_tokens_details").path("cached_tokens").asLong(0));
    }

    private void recordToken(String operation, String model, String tokenType, long tokens) {
        DistributionSummary.builder(TOKEN_USAGE)
                .baseUnit("tokens")
                .description("OpenAI token usage by generation operation")
                .tags(commonTags(operation, model).and("type", tokenType))
                .register(meterRegistry)
                .record(tokens);
    }

    private Tags commonTags(String operation, String model) {
        return Tags.of(
                "operation", bounded(operation, OPERATIONS),
                "model", model == null || model.isBlank() ? "unknown" : model
        );
    }

    private String bounded(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : "unknown";
    }
}
