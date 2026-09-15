package com.auknowlog.backend.quality.controller;

import com.auknowlog.backend.quality.dto.DuplicateEvaluationRequest;
import com.auknowlog.backend.quality.dto.DuplicateDatasetEmbeddingRequest;
import com.auknowlog.backend.quality.dto.DuplicateDatasetResponse;
import com.auknowlog.backend.quality.dto.DuplicateReviewRequest;
import com.auknowlog.backend.quality.dto.ObjectiveEvaluationRequest;
import com.auknowlog.backend.quality.dto.ObjectiveReviewRequest;
import com.auknowlog.backend.quality.dto.QualityEvaluationSummary;
import com.auknowlog.backend.quality.dto.QualityReviewQueue;
import com.auknowlog.backend.quality.dto.QualityRoadmapStepOption;
import com.auknowlog.backend.quality.dto.QualityRunResponse;
import com.auknowlog.backend.quality.service.QualityEvaluationService;
import com.auknowlog.backend.quality.service.DuplicateEvaluationDatasetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quality-evaluations")
public class QualityEvaluationController {

    private final QualityEvaluationService service;
    private final DuplicateEvaluationDatasetService datasetService;

    public QualityEvaluationController(QualityEvaluationService service,
                                       DuplicateEvaluationDatasetService datasetService) {
        this.service = service;
        this.datasetService = datasetService;
    }

    @GetMapping("/summary")
    public QualityEvaluationSummary summary() {
        return service.summary();
    }

    @GetMapping("/reviews")
    public QualityReviewQueue reviews(@RequestParam(defaultValue = "30") int limit) {
        return service.reviewQueue(limit);
    }

    @GetMapping("/roadmap-steps")
    public List<QualityRoadmapStepOption> roadmapSteps() {
        return service.roadmapSteps();
    }

    @GetMapping("/datasets")
    public List<DuplicateDatasetResponse> datasets() {
        return datasetService.listDatasets();
    }

    @PostMapping("/datasets/standard")
    @ResponseStatus(HttpStatus.CREATED)
    public DuplicateDatasetResponse createStandardDataset() {
        return datasetService.createStandardDataset();
    }

    @PostMapping("/datasets/{datasetId}/embeddings")
    public DuplicateDatasetResponse embedDataset(@PathVariable long datasetId,
                                                 @Valid @RequestBody DuplicateDatasetEmbeddingRequest request) {
        return datasetService.embedDataset(datasetId);
    }

    @PostMapping("/duplicate-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityRunResponse evaluateDuplicates(@Valid @RequestBody DuplicateEvaluationRequest request) {
        return service.evaluateDuplicateThreshold(request.maxPairs());
    }

    @PostMapping("/objective-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityRunResponse evaluateObjectives(@Valid @RequestBody ObjectiveEvaluationRequest request) {
        return service.evaluateObjectiveQuality(request.roadmapStepId());
    }

    @PutMapping("/duplicate-pairs/{pairId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewDuplicate(@PathVariable long pairId,
                                @Valid @RequestBody DuplicateReviewRequest request) {
        service.reviewDuplicate(pairId, request.verdict());
    }

    @PutMapping("/objective-cases/{caseId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reviewObjective(@PathVariable long caseId,
                                @Valid @RequestBody ObjectiveReviewRequest request) {
        service.reviewObjective(caseId, request.verdict());
    }
}
