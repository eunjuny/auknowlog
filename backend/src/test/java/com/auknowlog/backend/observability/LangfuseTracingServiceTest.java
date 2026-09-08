package com.auknowlog.backend.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LangfuseTracingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disabledTracingDoesNotRequireKeysOrSendRequests() {
        LangfuseTracingService service = new LangfuseTracingService(
                objectMapper, false, "", "", "https://cloud.langfuse.com", "test", false
        );

        assertThatCode(() -> {
            try (LangfuseTracingService.TraceScope trace = service.startQuizGeneration("Kubernetes", 5, false)) {
                trace.complete(Map.of("generatedQuestionCount", 5));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void exportsRedactedTraceToConfiguredOtlpEndpoint() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<HttpExchange> exchangeReference = new AtomicReference<>();
        AtomicReference<byte[]> bodyReference = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/otel/v1/traces", exchange -> respond(exchange, exchangeReference, bodyReference, received));
        server.start();

        LangfuseTracingService service = new LangfuseTracingService(
                objectMapper,
                true,
                "pk-lf-test",
                "sk-lf-test",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test",
                false
        );
        try {
            try (LangfuseTracingService.TraceScope trace = service.startQuizGeneration("internal-topic", 5, false)) {
                try (LangfuseTracingService.TraceScope generation = service.startGeneration(
                        "quiz-model-generation", "gpt-5.4-mini", Map.of("requestedQuestionCount", 5), Map.of("reasoningEffort", "low")
                )) {
                    generation.recordUsage(10, 20, 30);
                    generation.complete(Map.of("returnedQuestionCount", 5));
                }
                trace.complete(Map.of("generatedQuestionCount", 5));
            }
            service.shutdown();

            assertThat(received.await(3, TimeUnit.SECONDS)).isTrue();
            HttpExchange exchange = exchangeReference.get();
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/api/public/otel/v1/traces");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Basic ");
            assertThat(exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version")).isEqualTo("4");
            assertThat(bodyReference.get()).isNotEmpty();
            assertThat(new String(bodyReference.get(), StandardCharsets.ISO_8859_1)).doesNotContain("internal-topic");
        } finally {
            service.shutdown();
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange,
                         AtomicReference<HttpExchange> exchangeReference,
                         AtomicReference<byte[]> bodyReference,
                         CountDownLatch received) throws IOException {
        exchangeReference.set(exchange);
        bodyReference.set(exchange.getRequestBody().readAllBytes());
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
        received.countDown();
    }
}
