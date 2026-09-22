package com.auknowlog.backend.daily.controller;

import com.auknowlog.backend.daily.dto.*;
import com.auknowlog.backend.daily.service.DailyLearningService;
import com.auknowlog.backend.quiz.dto.QuizViewResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-learnings")
public class DailyLearningController {
    private final DailyLearningService service;
    public DailyLearningController(DailyLearningService service) { this.service = service; }

    @GetMapping("/today") public DailyLearningResponse today() { return service.today(); }
    @GetMapping("/{id}") public DailyLearningResponse get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/generate") public DailyLearningResponse generate(@Valid @RequestBody(required = false) DailyGenerationRequest request) {
        return service.generateToday(request == null ? null : request.articleUrl());
    }
    @PostMapping("/{id}/review-quiz") public QuizViewResponse reviewQuiz(@PathVariable Long id) { return service.createReviewQuiz(id); }
    @PostMapping("/{id}/advanced-quiz") public QuizViewResponse advancedQuiz(@PathVariable Long id, @Valid @RequestBody DailyAdvancedQuizRequest request) {
        return service.createAdvancedQuiz(id, request.topic(), request.numberOfQuestions());
    }
}
