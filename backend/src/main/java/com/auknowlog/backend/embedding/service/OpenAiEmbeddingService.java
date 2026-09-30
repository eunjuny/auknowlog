package com.auknowlog.backend.embedding.service;

import com.auknowlog.backend.common.exception.OpenAiUnavailableException;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "auknowlog.openai.embedding", name = "enabled", havingValue = "true")
public class OpenAiEmbeddingService implements EmbeddingService {

    private final RestClient restClient;
    private final LangfuseTracingService langfuseTracingService;
    private final com.auknowlog.backend.ai.service.AiGenerationLedgerService ledger;
    @org.springframework.beans.factory.annotation.Autowired
    private com.auknowlog.backend.ai.service.AiUsagePolicyService budget;

    @Value("${auknowlog.openai.api.key:}")
    private String apiKey;

    @Value("${auknowlog.openai.embedding.api-url:https://api.openai.com/v1/embeddings}")
    private String apiUrl;

    @Value("${auknowlog.openai.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${auknowlog.openai.embedding.dimensions:512}")
    private int dimensions;

    public OpenAiEmbeddingService(RestClient.Builder restClientBuilder,
                                  LangfuseTracingService langfuseTracingService,
                                  com.auknowlog.backend.ai.service.AiGenerationLedgerService ledger) {
        this.restClient = restClientBuilder.build();
        this.langfuseTracingService = langfuseTracingService;
        this.ledger = ledger;
    }

    @Override
    public Optional<EmbeddingResult> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiUnavailableException("임베딩 기능이 활성화되어 있지만 OpenAI API 키가 없습니다.");
        }

        long started = System.nanoTime();
        Long measuredTokens = null;
        try (LangfuseTracingService.TraceScope trace = langfuseTracingService
                .startEmbedding(model, dimensions, text.length())) {
            try {
                Map<String,Object> payload = Map.of("model", model, "input", text, "dimensions", dimensions, "encoding_format", "float");
                java.util.function.Supplier<JsonNode> call = () -> restClient.post()
                        .uri(apiUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "model", model,
                                "input", text,
                                "dimensions", dimensions,
                                "encoding_format", "float"
                        ))
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode response = budget == null ? call.get() : budget.execute("EMBEDDING", payload.toString(), 0, call);

                if (response != null && response.path("usage").has("prompt_tokens")) {
                    measuredTokens = response.path("usage").path("prompt_tokens").asLong();
                }
                JsonNode values = response == null ? null : response.path("data").path(0).path("embedding");
                if (values == null || !values.isArray() || values.size() != dimensions) {
                    throw new OpenAiUnavailableException("OpenAI 임베딩 응답의 차원이 설정값과 다릅니다.");
                }

                float[] vector = new float[values.size()];
                for (int index = 0; index < values.size(); index++) {
                    vector[index] = (float) values.get(index).asDouble();
                }
                long inputTokens = response.path("usage").path("prompt_tokens").asLong(0);
                trace.recordUsage(inputTokens, 0, inputTokens);
                trace.complete(Map.of("vectorDimensions", vector.length));
                ledger.recordEmbedding(model, measuredTokens,
                        java.time.Duration.ofNanos(System.nanoTime() - started), null);
                return Optional.of(new EmbeddingResult(
                        response.path("model").asText(model),
                        vector,
                        inputTokens
                ));
            } catch (RuntimeException e) {
                if (e instanceof com.auknowlog.backend.ai.service.AiBudgetExceededException) throw e;
                ledger.recordEmbedding(model, measuredTokens,
                        java.time.Duration.ofNanos(System.nanoTime() - started), e.getClass().getSimpleName());
                trace.fail(e);
                throw e;
            }
        }
    }
}
