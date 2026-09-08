package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.roadmap.dto.AiRoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import org.springframework.stereotype.Service;

@Service
public class AiRoadmapGenerationService {

    private final OpenAiRoadmapService openAiRoadmapService;
    private final LearningRoadmapService learningRoadmapService;

    public AiRoadmapGenerationService(OpenAiRoadmapService openAiRoadmapService,
                                      LearningRoadmapService learningRoadmapService) {
        this.openAiRoadmapService = openAiRoadmapService;
        this.learningRoadmapService = learningRoadmapService;
    }

    public LearningRoadmapSummary generate(AiRoadmapCreateRequest request) {
        RoadmapDefinitionRequest definition = openAiRoadmapService.generate(
                request.topic().trim(), request.durationWeeks(), request.stepCount());
        return learningRoadmapService.createAiGenerated(definition);
    }
}
