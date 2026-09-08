package com.auknowlog.backend.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Langfuse v4의 OTLP/HTTP 엔드포인트에 AI 워크플로를 선택적으로 기록한다.
 *
 * <p>기본값은 비활성화다. 따라서 키가 없거나 Langfuse가 일시적으로 실패해도
 * 퀴즈 생성 흐름과 OpenAI 호출은 영향을 받지 않는다. 기본적으로 원문 프롬프트와
 * 생성 문제는 보내지 않으며, {@code auknowlog.langfuse.capture-content=true}일 때만
 * 주제 문자열을 전송한다.</p>
 */
@Service
public class LangfuseTracingService {

    private static final Logger log = LoggerFactory.getLogger(LangfuseTracingService.class);
    private static final String OBSERVATION_TYPE = "langfuse.observation.type";
    private static final String OBSERVATION_INPUT = "langfuse.observation.input";
    private static final String OBSERVATION_OUTPUT = "langfuse.observation.output";
    private static final String OBSERVATION_MODEL = "langfuse.observation.model.name";
    private static final String OBSERVATION_MODEL_PARAMETERS = "langfuse.observation.model.parameters";
    private static final String OBSERVATION_USAGE = "langfuse.observation.usage_details";
    private static final String OBSERVATION_STATUS_MESSAGE = "langfuse.observation.status_message";
    private static final String TRACE_ENVIRONMENT = "langfuse.trace.environment";
    private static final String TRACE_METADATA_PREFIX = "langfuse.trace.metadata.";

    private final ObjectMapper objectMapper;
    private final boolean captureContent;
    private final String environment;
    private final Tracer tracer;
    private final SdkTracerProvider tracerProvider;

    public LangfuseTracingService(ObjectMapper objectMapper,
                                  @Value("${auknowlog.langfuse.enabled:false}") boolean enabled,
                                  @Value("${auknowlog.langfuse.public-key:}") String publicKey,
                                  @Value("${auknowlog.langfuse.secret-key:}") String secretKey,
                                  @Value("${auknowlog.langfuse.base-url:https://cloud.langfuse.com}") String baseUrl,
                                  @Value("${auknowlog.langfuse.environment:development}") String environment,
                                  @Value("${auknowlog.langfuse.capture-content:false}") boolean captureContent) {
        this.objectMapper = objectMapper;
        this.captureContent = captureContent;
        this.environment = environment;

        Tracer configuredTracer = null;
        SdkTracerProvider configuredProvider = null;
        if (enabled && !publicKey.isBlank() && !secretKey.isBlank()) {
            try {
                OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                        .setEndpoint(normalizeEndpoint(baseUrl))
                        .addHeader("Authorization", basicAuthorization(publicKey, secretKey))
                        .addHeader("x-langfuse-ingestion-version", "4")
                        .setTimeout(Duration.ofSeconds(2))
                        .build();
                Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "auknowlog"
                )));
                configuredProvider = SdkTracerProvider.builder()
                        .setResource(resource)
                        .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                                .setScheduleDelay(Duration.ofMillis(500))
                                .setMaxExportBatchSize(64)
                                .setMaxQueueSize(1_024)
                                .build())
                        .build();
                configuredTracer = OpenTelemetrySdk.builder().setTracerProvider(configuredProvider).build()
                        .getTracer("auknowlog.langfuse", "1.0");
                log.info("Langfuse tracing enabled for environment '{}' (content capture: {})", environment, captureContent);
            } catch (RuntimeException exception) {
                log.warn("Langfuse tracing could not be initialized; continuing without external tracing", exception);
            }
        } else if (enabled) {
            log.warn("Langfuse tracing is enabled but no project keys are configured; tracing remains disabled");
        }
        this.tracer = configuredTracer;
        this.tracerProvider = configuredProvider;
    }

    public TraceScope startQuizGeneration(String topic, int targetCount, boolean sourceProvided) {
        return start("quiz-generation", "chain", Map.of(
                "topic", safeTopic(topic),
                "requestedQuestionCount", targetCount,
                "sourceProvided", sourceProvided
        ));
    }

    public TraceScope startOperation(String name, Map<String, ?> input) {
        return start(name, "span", input);
    }

    public TraceScope startGeneration(String name, String model, Map<String, ?> input, Map<String, ?> modelParameters) {
        TraceScope scope = start(name, "generation", input);
        scope.configureModel(model, modelParameters);
        return scope;
    }

    public TraceScope startEmbedding(String model, int dimensions, int inputLength) {
        TraceScope scope = start("question-embedding", "embedding", Map.of("inputLength", inputLength));
        scope.configureModel(model, Map.of("dimensions", dimensions));
        return scope;
    }

    public static TraceScope noopScope() {
        return NoopTraceScope.INSTANCE;
    }

    private TraceScope start(String name, String observationType, Map<String, ?> input) {
        if (tracer == null) {
            return NoopTraceScope.INSTANCE;
        }

        try {
            Span span = tracer.spanBuilder(name).setSpanKind(SpanKind.INTERNAL).startSpan();
            span.setAttribute(OBSERVATION_TYPE, observationType);
            span.setAttribute(OBSERVATION_INPUT, json(input));
            span.setAttribute(TRACE_ENVIRONMENT, environment);
            span.setAttribute(TRACE_METADATA_PREFIX + "feature", "quiz-generation");
            return new OpenTelemetryTraceScope(span, span.makeCurrent());
        } catch (RuntimeException exception) {
            log.warn("Langfuse trace creation failed; continuing without this trace", exception);
            return NoopTraceScope.INSTANCE;
        }
    }

    private String safeTopic(String topic) {
        if (captureContent) {
            return topic;
        }
        return "sha256:" + shortHash(topic);
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String normalizeEndpoint(String baseUrl) {
        return baseUrl.replaceAll("/+$", "") + "/api/public/otel/v1/traces";
    }

    private String basicAuthorization(String publicKey, String secretKey) {
        String credentials = publicKey + ":" + secretKey;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @PreDestroy
    void shutdown() {
        if (tracerProvider != null) {
            tracerProvider.forceFlush().join(2, TimeUnit.SECONDS);
            tracerProvider.shutdown().join(2, TimeUnit.SECONDS);
        }
    }

    public interface TraceScope extends AutoCloseable {

        void complete(Map<String, ?> output);

        void recordUsage(long inputTokens, long outputTokens, long totalTokens);

        void configureModel(String model, Map<String, ?> modelParameters);

        void fail(Throwable exception);

        @Override
        void close();
    }

    private final class OpenTelemetryTraceScope implements TraceScope {

        private final Span span;
        private final Scope scope;
        private boolean closed;

        private OpenTelemetryTraceScope(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }

        @Override
        public void complete(Map<String, ?> output) {
            span.setAttribute(OBSERVATION_OUTPUT, json(output));
        }

        @Override
        public void recordUsage(long inputTokens, long outputTokens, long totalTokens) {
            span.setAttribute(OBSERVATION_USAGE, json(Map.of(
                    "input", inputTokens,
                    "output", outputTokens,
                    "total", totalTokens
            )));
        }

        @Override
        public void fail(Throwable exception) {
            String type = exception == null ? "unknown" : exception.getClass().getSimpleName();
            span.setStatus(StatusCode.ERROR, type);
            span.setAttribute(OBSERVATION_STATUS_MESSAGE, type);
        }

        @Override
        public void configureModel(String model, Map<String, ?> modelParameters) {
            span.setAttribute(OBSERVATION_MODEL, model);
            span.setAttribute(OBSERVATION_MODEL_PARAMETERS, json(modelParameters));
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            scope.close();
            span.end();
        }
    }

    private static final class NoopTraceScope implements TraceScope {

        private static final NoopTraceScope INSTANCE = new NoopTraceScope();

        @Override
        public void complete(Map<String, ?> output) {
        }

        @Override
        public void recordUsage(long inputTokens, long outputTokens, long totalTokens) {
        }

        @Override
        public void configureModel(String model, Map<String, ?> modelParameters) {
        }

        @Override
        public void fail(Throwable exception) {
        }

        @Override
        public void close() {
        }
    }
}
