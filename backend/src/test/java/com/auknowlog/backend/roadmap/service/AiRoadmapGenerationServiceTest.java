package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.roadmap.dto.AiRoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapStepDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRoadmapGenerationServiceTest {

    @Mock
    private OpenAiRoadmapService openAiRoadmapService;

    @Mock
    private LearningRoadmapService learningRoadmapService;

    @Test
    void usesTheSameVersionedDefinitionContractForAiGeneratedRoadmaps() {
        RoadmapDefinitionRequest generated = new RoadmapDefinitionRequest(
                "1.0", "Kubernetes 학습", "Kubernetes", "단계형 학습", 4,
                List.of(
                        new RoadmapStepDefinition("core", "핵심", "핵심 개념", "Kubernetes 기초", 5, List.of()),
                        new RoadmapStepDefinition("network", "네트워크", "통신 흐름", "Kubernetes 네트워크", 5, List.of("core"))
                ));
        when(openAiRoadmapService.generate("Kubernetes", 4, 2)).thenReturn(generated);

        AiRoadmapGenerationService service = new AiRoadmapGenerationService(openAiRoadmapService, learningRoadmapService);
        service.generate(new AiRoadmapCreateRequest(" Kubernetes ", 4, 2));

        verify(learningRoadmapService).createAiGenerated(generated);
    }
}
