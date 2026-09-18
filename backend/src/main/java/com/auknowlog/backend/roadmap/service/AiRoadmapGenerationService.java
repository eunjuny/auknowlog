package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.roadmap.dto.AiRoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.AiRoadmapConfirmRequest;
import com.auknowlog.backend.roadmap.dto.AiRoadmapPreviewResponse;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.source.dto.SourceRoadmapContext;
import com.auknowlog.backend.source.service.SourceService;
import org.springframework.stereotype.Service;

@Service
public class AiRoadmapGenerationService {

    private final OpenAiRoadmapService openAiRoadmapService;
    private final LearningRoadmapService learningRoadmapService;
    private final SourceService sourceService;

    public AiRoadmapGenerationService(OpenAiRoadmapService openAiRoadmapService,
                                      LearningRoadmapService learningRoadmapService,
                                      SourceService sourceService) {
        this.openAiRoadmapService = openAiRoadmapService;
        this.learningRoadmapService = learningRoadmapService;
        this.sourceService = sourceService;
    }

    public AiRoadmapPreviewResponse generatePreview(AiRoadmapCreateRequest request) {
        SourceRoadmapContext sourceContext = request.sourceId() == null
                ? null
                : sourceService.getRoadmapContext(request.sourceId(), request.topic().trim());
        RoadmapDefinitionRequest definition = openAiRoadmapService.generate(
                request.topic().trim(), request.durationWeeks(), sourceContext);
        learningRoadmapService.validateDraft(definition);
        return new AiRoadmapPreviewResponse(
                definition,
                sourceContext == null ? null : sourceContext.sourceId(),
                sourceContext == null ? null : sourceContext.title(),
                sourceContext == null ? null : sourceContext.sourceUri()
        );
    }

    public LearningRoadmapSummary confirm(AiRoadmapConfirmRequest request) {
        return learningRoadmapService.createAiGenerated(
                request.definition(),
                request.sourceId()
        );
    }
}
