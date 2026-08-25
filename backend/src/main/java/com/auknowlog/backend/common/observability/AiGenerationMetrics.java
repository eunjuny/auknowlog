package com.auknowlog.backend.common.observability;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OpenAI 호출의 성공률, 지연 시간, 토큰 사용량을 모델별로 기록한다.
 *
 * 가격은 공급자 정책에 따라 바뀌므로 애플리케이션에서 금액으로 환산하지 않고,
 * 실제 사용량만 기록해 대시보드 또는 비용 분석 도구에서 해석하도록 한다.
 */
@Component
public class AiGenerationMetrics {

    private static final String REQUEST_DURATION = "auknowlog.ai.quiz.request.duration";
    private static final String TOKEN_USAGE = "auknowlog.ai.quiz.tokens";

    private final MeterRegistry meterRegistry;

    public AiGenerationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess(String model, JsonNode usage, Duration duration) {
        recordRequest(model, "success", duration);
        recordTokenUsage(model, usage);
    }

    public void recordFailure(String model, String failureType, Duration duration) {
        recordRequest(model, failureType, duration);
    }

    private void recordRequest(String model, String outcome, Duration duration) {
        Timer.builder(REQUEST_DURATION)
                .description("Duration of an OpenAI quiz generation request")
                .tags(commonTags(model).and("outcome", outcome))
                .register(meterRegistry)
                .record(duration);
    }

    private void recordTokenUsage(String model, JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return;
        }

        recordToken(model, "input", usage.path("input_tokens").asLong(0));
        recordToken(model, "output", usage.path("output_tokens").asLong(0));
        recordToken(model, "total", usage.path("total_tokens").asLong(0));
        recordToken(model, "reasoning", usage.path("output_tokens_details").path("reasoning_tokens").asLong(0));
        recordToken(model, "cached_input", usage.path("input_tokens_details").path("cached_tokens").asLong(0));
    }

    private void recordToken(String model, String tokenType, long tokens) {
        DistributionSummary.builder(TOKEN_USAGE)
                .baseUnit("tokens")
                .description("OpenAI token usage for quiz generation")
                .tags(commonTags(model).and("type", tokenType))
                .register(meterRegistry)
                .record(tokens);
    }

    private Tags commonTags(String model) {
        return Tags.of("model", model == null || model.isBlank() ? "unknown" : model);
    }
}
