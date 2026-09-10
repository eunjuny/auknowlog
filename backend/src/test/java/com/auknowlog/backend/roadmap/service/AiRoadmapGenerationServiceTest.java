package com.auknowlog.backend.roadmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.auknowlog.backend.roadmap.dto.AiRoadmapConfirmRequest;
import com.auknowlog.backend.roadmap.dto.AiRoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.AiRoadmapPreviewResponse;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapStepDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapSubtopicDefinition;
import com.auknowlog.backend.source.dto.SourceChunkContext;
import com.auknowlog.backend.source.dto.SourceRoadmapContext;
import com.auknowlog.backend.source.entity.SourceType;
import com.auknowlog.backend.source.service.SourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRoadmapGenerationServiceTest {

    @Mock
    private OpenAiRoadmapService openAiRoadmapService;

    @Mock
    private LearningRoadmapService learningRoadmapService;

    @Mock
    private SourceService sourceService;

    @Test
    void adaptiveSchemaUsesOnlyAbsoluteTopicSafetyLimits() {
        JsonNode schema = new ObjectMapper().valueToTree(OpenAiRoadmapService.schema());
        String prompt = OpenAiRoadmapService.basePrompt("Kubernetes", 8);

        assertThat(schema.at("/properties/steps/minItems").asInt()).isEqualTo(1);
        assertThat(schema.at("/properties/steps/maxItems").asInt()).isEqualTo(10);
        assertThat(schema.at("/properties/steps/items/properties/subtopics/minItems").asInt()).isEqualTo(1);
        assertThat(schema.at("/properties/steps/items/properties/subtopics/maxItems").asInt()).isEqualTo(10);
        assertThat(schema.at("/properties/steps/items/properties/questionTarget/maximum").asInt()).isEqualTo(200);
        assertThat(schema.at("/properties/steps/items/properties/subtopics/items/properties/questionTarget/maximum").asInt()).isEqualTo(20);
        assertThat(prompt)
                .contains("Decide the appropriate number of major topics, subtopics, and mastery questions")
                .contains("Do not pad or truncate the curriculum to meet a fixed item quota")
                .contains("Prefer the smallest focused curriculum that still covers the learning goal")
                .contains("Never treat schema maxima as targets");
    }

    @Test
    void generatesEditablePreviewWithoutPersistingIt() {
        RoadmapDefinitionRequest generated = new RoadmapDefinitionRequest(
                "1.1", "Kubernetes 학습", "Kubernetes", "계층형 학습", 4,
                List.of(
                        new RoadmapStepDefinition("core", "Kubernetes 기초", "핵심 개념", "Kubernetes 기초", 10,
                                List.of(), List.of(
                                new RoadmapSubtopicDefinition("pod", "Pod", "실행 단위", "Kubernetes Pod", 5),
                                new RoadmapSubtopicDefinition("service", "Service", "네트워크", "Kubernetes Service", 5)
                        )),
                        new RoadmapStepDefinition("operations", "Kubernetes 운영", "운영 흐름", "Kubernetes 운영", 5,
                                List.of("core"), List.of())
                ));
        when(openAiRoadmapService.generate("Kubernetes", 4, null)).thenReturn(generated);

        AiRoadmapGenerationService service = new AiRoadmapGenerationService(
                openAiRoadmapService, learningRoadmapService, sourceService);
        AiRoadmapPreviewResponse preview = service.generatePreview(new AiRoadmapCreateRequest(" Kubernetes ", 4));

        assertThat(preview.definition()).isEqualTo(generated);
        assertThat(preview.sourceDocumentId()).isNull();
        verify(openAiRoadmapService).generate("Kubernetes", 4, null);
        verify(learningRoadmapService).validateDraft(generated);
        verify(learningRoadmapService, never()).createAiGenerated(any(), any());
    }

    @Test
    void returnsSourceProvenanceWithTheEditablePreview() {
        SourceRoadmapContext context = new SourceRoadmapContext(
                3L,
                "AI 에이전트 보안 기사",
                SourceType.URL,
                "https://example.com/article",
                List.of(new SourceChunkContext("source-3-chunk-1", "비신뢰 학습 자료 본문"))
        );
        RoadmapDefinitionRequest generated = new RoadmapDefinitionRequest(
                "1.1", "AI 에이전트 보안", "AI 에이전트 보안", "자료 기반 로드맵", 3,
                List.of(
                        new RoadmapStepDefinition("foundation", "기초", "기초 이해", "AI 보안 기초", 6,
                                List.of(), List.of(
                                new RoadmapSubtopicDefinition("agent", "AI 에이전트", "개념", "AI 에이전트", 3),
                                new RoadmapSubtopicDefinition("threat", "위협 모델", "공격", "AI 위협 모델", 3)
                        )),
                        new RoadmapStepDefinition("defense", "방어", "보안 통제", "AI 방어", 6,
                                List.of("foundation"), List.of(
                                new RoadmapSubtopicDefinition("sandbox", "샌드박스", "격리", "AI 샌드박스", 3),
                                new RoadmapSubtopicDefinition("eval", "평가", "검증", "AI 안전성 평가", 3)
                        ))
                ));
        when(sourceService.getRoadmapContext(3L)).thenReturn(context);
        when(openAiRoadmapService.generate("AI 에이전트 보안", 3, context)).thenReturn(generated);

        AiRoadmapGenerationService service = new AiRoadmapGenerationService(
                openAiRoadmapService, learningRoadmapService, sourceService);
        AiRoadmapPreviewResponse preview = service.generatePreview(
                new AiRoadmapCreateRequest("AI 에이전트 보안", 3, 3L));

        assertThat(preview.sourceDocumentId()).isEqualTo(3L);
        assertThat(preview.sourceDocumentTitle()).isEqualTo("AI 에이전트 보안 기사");
        assertThat(preview.sourceDocumentUri()).isEqualTo("https://example.com/article");
        verify(openAiRoadmapService).generate("AI 에이전트 보안", 3, context);
        verify(learningRoadmapService).validateDraft(generated);
        verify(learningRoadmapService, never()).createAiGenerated(any(), any());
    }

    @Test
    void persistsOnlyTheUserConfirmedEditedDefinition() {
        RoadmapDefinitionRequest edited = new RoadmapDefinitionRequest(
                "1.1", "수정한 AI 보안 로드맵", "AI 에이전트 보안", "사용자가 검토한 구성", 3,
                List.of(new RoadmapStepDefinition(
                        "foundation", "수정한 기초", "수정한 설명", "AI 보안 기초", 4,
                        List.of(), List.of(new RoadmapSubtopicDefinition(
                        "threat", "위협 모델", "공격 경로", "AI 위협 모델", 4
                )))));
        AiRoadmapGenerationService service = new AiRoadmapGenerationService(
                openAiRoadmapService, learningRoadmapService, sourceService);

        service.confirm(new AiRoadmapConfirmRequest(edited, 3L));

        verifyNoInteractions(openAiRoadmapService);
        verify(learningRoadmapService).createAiGenerated(edited, 3L);
    }
}
