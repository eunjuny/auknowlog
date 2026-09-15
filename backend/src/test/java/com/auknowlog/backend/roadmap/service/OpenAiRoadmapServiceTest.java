package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.ai.service.AiGenerationLedgerService;
import com.auknowlog.backend.common.observability.AiGenerationMetrics;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapLearningObjectiveDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapStepDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapSubtopicDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiRoadmapServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiRoadmapService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        LangfuseTracingService tracingService = mock(LangfuseTracingService.class);
        when(tracingService.startGeneration(any(), any(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap())).thenReturn(LangfuseTracingService.noopScope());
        service = new OpenAiRoadmapService(
                builder,
                objectMapper,
                new AiGenerationMetrics(new SimpleMeterRegistry()),
                mock(AiGenerationLedgerService.class),
                tracingService
        );
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.openai.com/v1/responses");
        ReflectionTestUtils.setField(service, "modelName", "gpt-5.4-mini");
        ReflectionTestUtils.setField(service, "reasoningEffort", "low");
    }

    @Test
    void generatesVersion12ObjectivesAndDerivesQuestionTargetsFromTheirCoverage() throws Exception {
        RoadmapDefinitionRequest modelDefinition = new RoadmapDefinitionRequest(
                "1.2", "Kubernetes 핵심", "ignored", "핵심 개념", 99,
                List.of(new RoadmapStepDefinition(
                        "foundation", "기초", "기초 개념", "Kubernetes", 19, List.of(),
                        List.of(new RoadmapSubtopicDefinition(
                                "pod", "Pod", "Pod 동작", "Kubernetes Pod", 19,
                                List.of(
                                        new RoadmapLearningObjectiveDefinition(
                                                "lifecycle", "생명주기", "상태 전이", "CORE", 2),
                                        new RoadmapLearningObjectiveDefinition(
                                                "probe", "프로브", "상태 확인", "CORE", 1)
                                )
                        )),
                        List.of()
                ))
        );
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "model", "gpt-5.4-mini",
                "usage", Map.of("input_tokens", 20, "output_tokens", 10, "total_tokens", 30),
                "output", List.of(Map.of("type", "message", "content", List.of(Map.of(
                        "type", "output_text", "text", objectMapper.writeValueAsString(modelDefinition)
                ))))
        ));

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(jsonPath("$.text.format.schema.properties.version.enum[0]").value("1.2"))
                .andExpect(jsonPath("$.input[0].content[0].text", containsString(
                        "analyze the essential knowledge and practical decisions")))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        RoadmapDefinitionRequest result = service.generate("Kubernetes", 4);

        assertThat(result.version()).isEqualTo("1.2");
        assertThat(result.topic()).isEqualTo("Kubernetes");
        assertThat(result.durationWeeks()).isEqualTo(4);
        assertThat(result.steps().getFirst().questionTarget()).isEqualTo(3);
        assertThat(result.steps().getFirst().subtopics().getFirst().questionTarget()).isEqualTo(3);
        assertThat(result.steps().getFirst().subtopics().getFirst().learningObjectives()).hasSize(2);
        server.verify();
    }
}
