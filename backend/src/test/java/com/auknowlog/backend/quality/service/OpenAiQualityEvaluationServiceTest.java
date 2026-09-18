package com.auknowlog.backend.quality.service;

import com.auknowlog.backend.ai.service.AiGenerationLedgerService;
import com.auknowlog.backend.ai.service.AiUsagePolicyService;
import com.auknowlog.backend.common.observability.AiGenerationMetrics;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.ObjectiveInput;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.QuestionInput;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.StepEvaluationInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiQualityEvaluationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QualityEvaluationRepository repository;
    private AiGenerationLedgerService ledgerService;
    private MockRestServiceServer server;
    private OpenAiQualityEvaluationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        repository = mock(QualityEvaluationRepository.class);
        ledgerService = mock(AiGenerationLedgerService.class);
        service = new OpenAiQualityEvaluationService(
                builder, objectMapper, repository,
                new AiGenerationMetrics(new SimpleMeterRegistry()), ledgerService,
                mock(AiUsagePolicyService.class)
        );
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.openai.com/v1/responses");
        ReflectionTestUtils.setField(service, "modelName", "gpt-5.4-mini");
        ReflectionTestUtils.setField(service, "reasoningEffort", "low");
        ReflectionTestUtils.setField(service, "autoAcceptConfidence", 0.85);
    }

    @Test
    void evaluatesObjectivesAndQueuesOnlyUncertainOrAdverseFindings() throws Exception {
        when(repository.findStepEvaluationInput(31L)).thenReturn(new StepEvaluationInput(
                31, "Kubernetes 기초", "Pod", "Kubernetes Pod",
                List.of(new ObjectiveInput(41, "pod-probe", "상태 프로브", "프로브 동작을 구분합니다.", "CORE")),
                List.of(new QuestionInput(51, "readiness probe 실패 시 어떻게 되나요?", 41L))
        ));
        when(repository.createRun("OBJECTIVE_QUALITY", "roadmap-step:31",
                "gpt-5.4-mini", "objective-quality-v1")).thenReturn(61L);

        Map<String, Object> missingObjective = new LinkedHashMap<>();
        missingObjective.put("key", "restart");
        missingObjective.put("title", "재시작 정책");
        missingObjective.put("importance", "CORE");
        missingObjective.put("matchedLearningObjectiveId", null);
        missingObjective.put("verdict", "MISSING");
        missingObjective.put("confidence", 0.91);
        missingObjective.put("rationale", "재시작 정책 목표가 없습니다.");
        String resultJson = objectMapper.writeValueAsString(Map.of(
                "referenceObjectives", List.of(
                        Map.of("key", "probe", "title", "상태 프로브", "importance", "CORE",
                                "matchedLearningObjectiveId", 41, "verdict", "COVERED",
                                "confidence", 0.94, "rationale", "필수 프로브 목표가 있습니다."),
                        missingObjective
                ),
                "questionAlignments", List.of(
                        Map.of("learningQuestionId", 51, "verdict", "ALIGNED",
                                "confidence", 0.92, "rationale", "프로브 동작을 직접 묻습니다.")
                )
        ));
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "model", "gpt-5.4-mini",
                "usage", Map.of("input_tokens", 100, "output_tokens", 80, "total_tokens", 180),
                "output", List.of(Map.of("type", "message", "content", List.of(Map.of(
                        "type", "output_text", "text", resultJson
                ))))
        ));

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.max_output_tokens").value(3000))
                .andExpect(jsonPath("$.input[0].content[0].text", containsString("assignedObjectiveId")))
                .andExpect(jsonPath("$.text.format.schema.properties.referenceObjectives.type").value("array"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        var response = service.evaluate(31L);

        assertThat(response.candidateCount()).isEqualTo(3);
        assertThat(response.reviewRequiredCount()).isEqualTo(1);
        assertThat(response.totalTokens()).isEqualTo(180);
        verify(repository).insertObjectiveCase(
                61, "OBJECTIVE_COVERAGE", "Kubernetes Pod", "probe", "상태 프로브", "CORE",
                41L, null, "COVERED", 0.94, "필수 프로브 목표가 있습니다.", "AUTO_ACCEPTED");
        verify(repository).insertObjectiveCase(
                61, "OBJECTIVE_COVERAGE", "Kubernetes Pod", "restart", "재시작 정책", "CORE",
                null, null, "MISSING", 0.91, "재시작 정책 목표가 없습니다.", "REVIEW_REQUIRED");
        verify(repository).completeRun(61, 3, 1, 100L, 80L, 180L, "gpt-5.4-mini");
        verify(ledgerService).recordQualitySuccess(any(), any(), any());
        server.verify();
    }
}
