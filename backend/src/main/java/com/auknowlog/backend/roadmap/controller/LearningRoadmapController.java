package com.auknowlog.backend.roadmap.controller;

import com.auknowlog.backend.roadmap.dto.ActiveLearningRoadmapResponse;
import com.auknowlog.backend.roadmap.dto.AiRoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.service.LearningRoadmapService;
import com.auknowlog.backend.roadmap.service.AiRoadmapGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/learning-roadmaps")
public class LearningRoadmapController {

    private final LearningRoadmapService learningRoadmapService;
    private final AiRoadmapGenerationService aiRoadmapGenerationService;

    public LearningRoadmapController(LearningRoadmapService learningRoadmapService,
                                     AiRoadmapGenerationService aiRoadmapGenerationService) {
        this.learningRoadmapService = learningRoadmapService;
        this.aiRoadmapGenerationService = aiRoadmapGenerationService;
    }

    @PostMapping
    public LearningRoadmapSummary create(@Valid @RequestBody RoadmapCreateRequest request) {
        return learningRoadmapService.create(request);
    }

    @PostMapping("/stages")
    public LearningRoadmapSummary createStageBased(@Valid @RequestBody RoadmapDefinitionRequest request) {
        return learningRoadmapService.createStageBased(request);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public LearningRoadmapSummary importStageBased(@RequestParam("file") MultipartFile file) throws IOException {
        return learningRoadmapService.importStageBased(file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping("/ai")
    public LearningRoadmapSummary createWithAi(@Valid @RequestBody AiRoadmapCreateRequest request) {
        return aiRoadmapGenerationService.generate(request);
    }

    @GetMapping("/active")
    public ActiveLearningRoadmapResponse active() {
        return learningRoadmapService.getActive();
    }

    @GetMapping("/{roadmapId}")
    public LearningRoadmapSummary detail(@PathVariable Long roadmapId) {
        return learningRoadmapService.getById(roadmapId);
    }
}
