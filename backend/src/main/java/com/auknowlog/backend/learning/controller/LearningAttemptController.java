package com.auknowlog.backend.learning.controller;

import com.auknowlog.backend.learning.dto.AttemptRequest;
import com.auknowlog.backend.learning.dto.AttemptResult;
import com.auknowlog.backend.learning.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
