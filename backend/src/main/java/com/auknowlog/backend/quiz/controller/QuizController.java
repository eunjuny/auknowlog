package com.auknowlog.backend.quiz.controller;

import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.quiz.service.GeminiService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final GeminiService geminiService;

    public QuizController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping
    public Mono<QuizResponse> createQuiz(@RequestBody QuizRequest request) {
        int questionsToGenerate = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        return geminiService.generateQuiz(request.topic(), questionsToGenerate);
    }
}
