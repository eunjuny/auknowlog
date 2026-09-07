package com.auknowlog.backend.learning.controller;

import com.auknowlog.backend.learning.dto.AttemptRequest;
import com.auknowlog.backend.learning.dto.AttemptResult;
import com.auknowlog.backend.learning.dto.LearningAttemptDetail;
import com.auknowlog.backend.learning.dto.LearningAttemptHistory;
import com.auknowlog.backend.learning.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning-attempts")
public class LearningAttemptController {

    private final LearningService learningService;

    public LearningAttemptController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public AttemptResult record(@Valid @RequestBody AttemptRequest request) {
        return learningService.recordAttempt(request);
    }

    @GetMapping
    public LearningAttemptHistory history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return learningService.getAttemptHistory(page, size);
    }

    @GetMapping("/{attemptId}")
    public LearningAttemptDetail detail(@PathVariable Long attemptId) {
        return learningService.getAttemptDetail(attemptId);
    }
}
