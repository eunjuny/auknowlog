package com.auknowlog.backend.quiz;

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
        return geminiService.generateQuiz(request.topic());
    }
}
