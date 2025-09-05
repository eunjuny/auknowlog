package com.auknowlog.backend.quiz.controller;

import com.auknowlog.backend.quiz.dto.Question;
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

import java.util.ArrayList;
import java.util.List;

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
    @PostMapping("/create")
    public Mono<QuizResponse> createQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @RequestBody QuizRequest request) {
        int questionsToGenerate = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        return geminiService.generateQuiz(request.topic(), questionsToGenerate);
    }

    @Operation(summary = "개발용 더미 퀴즈 생성", description = "실제 AI 호출 없이 더미 데이터로 퀴즈를 생성합니다. 개발 및 테스트용으로 사용됩니다.")
    @ApiResponse(responseCode = "200", description = "더미 퀴즈 생성 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuizResponse.class)))
    @PostMapping("/dummy")
    public Mono<QuizResponse> createDummyQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @RequestBody QuizRequest request) {
        int questionsToGenerate = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        return Mono.just(createDummyQuizResponse(request.topic(), questionsToGenerate));
    }

    private QuizResponse createDummyQuizResponse(String topic, int numberOfQuestions) {
        String quizTitle = topic != null ? topic + " 퀴즈" : "더미 퀴즈";

        List<Question> questions = new ArrayList<>();
        for (int i = 1; i <= numberOfQuestions; i++) {
            questions.add(new Question(
                "더미 문제 " + i + ": " + topic + "에 대한 질문입니다.",
                List.of("선택지 A", "선택지 B", "선택지 C", "선택지 D"),
                "선택지 A",
                "이것은 더미 데이터로 생성된 문제입니다. 정답은 선택지 A입니다."
            ));
        }

        return new QuizResponse(quizTitle, questions);
    }
}
