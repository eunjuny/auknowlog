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

    @Value("${auknowlog.openai.api.key:}")
    private String apiKey;

    @Value("${auknowlog.openai.embedding.api-url:https://api.openai.com/v1/embeddings}")
    private String apiUrl;

    @Value("${auknowlog.openai.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${auknowlog.openai.embedding.dimensions:512}")
    private int dimensions;

    public OpenAiEmbeddingService(RestClient.Builder restClientBuilder,
                                  LangfuseTracingService langfuseTracingService) {
        this.restClient = restClientBuilder.build();
        this.langfuseTracingService = langfuseTracingService;
    }

    @Override
    public Optional<EmbeddingResult> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiUnavailableException("임베딩 기능이 활성화되어 있지만 OpenAI API 키가 없습니다.");
        }

        try (LangfuseTracingService.TraceScope trace = langfuseTracingService
                .startEmbedding(model, dimensions, text.length())) {
            try {
                JsonNode response = restClient.post()
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
                return Optional.of(new EmbeddingResult(
                        response.path("model").asText(model),
                        vector,
                        inputTokens
                ));
            } catch (RuntimeException e) {
                trace.fail(e);
                throw e;
            }
        }
    }
}
