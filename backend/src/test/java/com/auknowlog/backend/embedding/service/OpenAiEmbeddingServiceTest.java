package com.auknowlog.backend.embedding.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiEmbeddingServiceTest {

    private MockRestServiceServer server;
    private OpenAiEmbeddingService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiEmbeddingService(builder);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.openai.com/v1/embeddings");
        ReflectionTestUtils.setField(service, "model", "text-embedding-3-small");
        ReflectionTestUtils.setField(service, "dimensions", 3);
    }

    @Test
    void requestsConfiguredDimensionsWithoutCallingLiveApi() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input").value("JVM은 바이트코드를 실행합니다."))
                .andExpect(jsonPath("$.dimensions").value(3))
                .andRespond(withSuccess("""
                        {
                          "model": "text-embedding-3-small",
                          "data": [{"embedding": [0.1, 0.2, 0.3]}],
                          "usage": {"prompt_tokens": 8}
                        }
                        """, MediaType.APPLICATION_JSON));

        EmbeddingResult result = service.embed("JVM은 바이트코드를 실행합니다.").orElseThrow();

        assertThat(result.model()).isEqualTo("text-embedding-3-small");
        assertThat(result.values()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.inputTokens()).isEqualTo(8);
        server.verify();
    }
}
