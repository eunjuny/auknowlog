package com.auknowlog.backend.ai.service;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AiGenerationLedgerService {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationLedgerService.class);

    private final AiGenerationLogRepository repository;

    public AiGenerationLedgerService(AiGenerationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQuizSuccess(String model, JsonNode usage, Duration duration) {
        record(new AiGenerationLog(
                "QUIZ_GENERATION",
                model,
                "SUCCESS",
                token(usage, "input_tokens"),
                token(usage, "output_tokens"),
                token(usage, "total_tokens"),
                duration.toMillis(),
                null
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQuizFailure(String model, String failureType, Duration duration) {
        record(new AiGenerationLog(
                "QUIZ_GENERATION",
                model,
                "FAILED",
                null,
                null,
                null,
                duration.toMillis(),
                failureType
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoadmapSuccess(String model, JsonNode usage, Duration duration) {
        record(new AiGenerationLog(
                "ROADMAP_GENERATION",
                model,
                "SUCCESS",
                token(usage, "input_tokens"),
                token(usage, "output_tokens"),
                token(usage, "total_tokens"),
                duration.toMillis(),
                null
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoadmapFailure(String model, String failureType, Duration duration) {
        record(new AiGenerationLog(
                "ROADMAP_GENERATION",
                model,
                "FAILED",
                null,
                null,
                null,
                duration.toMillis(),
                failureType
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQualitySuccess(String model, JsonNode usage, Duration duration) {
        record(new AiGenerationLog(
                "QUALITY_EVALUATION",
                model,
                "SUCCESS",
                token(usage, "input_tokens"),
                token(usage, "output_tokens"),
                token(usage, "total_tokens"),
                duration.toMillis(),
                null
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordQualityFailure(String model, String failureType, Duration duration) {
        record(new AiGenerationLog(
                "QUALITY_EVALUATION",
                model,
                "FAILED",
                null,
                null,
                null,
                duration.toMillis(),
                failureType
        ));
    }

    private Long token(JsonNode usage, String field) {
        if (usage == null || !usage.isObject() || !usage.has(field)) {
            return null;
        }
        return usage.path(field).asLong();
    }

    private void record(AiGenerationLog logEntry) {
        try {
            repository.save(logEntry);
        } catch (RuntimeException e) {
            // 원장 기록 실패가 사용자 요청까지 실패시키지는 않도록 분리한다.
            log.warn("AI generation ledger could not be persisted", e);
        }
    }
}
