package com.auknowlog.backend.common.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class SourceIngestionMetrics {

    private static final Set<String> SOURCE_TYPES = Set.of("file", "url");
    private static final Set<String> OUTCOMES = Set.of("success", "rejected", "failure");

    private final MeterRegistry meterRegistry;

    public SourceIngestionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String sourceType, String outcome, long inputBytes, int extractedCharacters, Duration duration) {
        String safeType = bounded(sourceType, SOURCE_TYPES);
        String safeOutcome = bounded(outcome, OUTCOMES);
        Timer.builder("auknowlog.source.preview.duration")
                .description("Duration of safe source preview processing")
                .tags("source_type", safeType, "outcome", safeOutcome)
                .register(meterRegistry)
                .record(duration);
        if (inputBytes >= 0) {
            DistributionSummary.builder("auknowlog.source.input.bytes")
                    .baseUnit("bytes")
                    .tags("source_type", safeType, "outcome", safeOutcome)
                    .register(meterRegistry)
                    .record(inputBytes);
        }
        if (extractedCharacters >= 0) {
            DistributionSummary.builder("auknowlog.source.extracted.characters")
                    .baseUnit("characters")
                    .tags("source_type", safeType, "outcome", safeOutcome)
                    .register(meterRegistry)
                    .record(extractedCharacters);
        }
    }

    private String bounded(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : "unknown";
    }
}
