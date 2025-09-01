package com.auknowlog.backend.quiz.controller;

import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.quiz.service.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Quiz API", description = "퀴즈 생성 및 관리를 위한 API")
@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final GeminiService geminiService;

    public QuizController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @Operation(summary = "새로운 퀴즈 생성", description = "주제와 문제 수를 기반으로 Gemini AI를 통해 새로운 객관식 퀴즈를 생성합니다.")
    @ApiResponse(responseCode = "200", description = "퀴즈 생성 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuizResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
            content = @Content(mediaType = "application/json"))
    @PostMapping
    public Mono<QuizResponse> createQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @RequestBody QuizRequest request) {
        int questionsToGenerate = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        return geminiService.generateQuiz(request.topic(), questionsToGenerate);
    }
}
