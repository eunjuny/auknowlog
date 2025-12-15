package com.auknowlog.backend.quiz.controller;

import com.auknowlog.backend.question.service.QuestionHistoryService;
import com.auknowlog.backend.question.service.QuestionSearchService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Quiz API", description = "퀴즈 생성 및 관리를 위한 API")
@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);

    private final GeminiService geminiService;
    private final QuestionHistoryService questionHistoryService;
    private final QuestionSearchService questionSearchService;

    public QuizController(GeminiService geminiService, 
                         QuestionHistoryService questionHistoryService,
                         QuestionSearchService questionSearchService) {
        this.geminiService = geminiService;
        this.questionHistoryService = questionHistoryService;
        this.questionSearchService = questionSearchService;
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
        int requested = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        int questionsToGenerate = Math.max(1, Math.min(20, requested));
        String topic = request.topic();

        return geminiService.generateQuiz(topic, questionsToGenerate)
                .flatMap(quizResponse -> 
                    // 1. ES 유사도 기반 중복 체크 후 필터링
                    filterDuplicateQuestions(quizResponse.questions())
                        .flatMap(filteredQuestions -> {
                            log.info("유사도 필터링 후 문제 수: {}/{}", filteredQuestions.size(), quizResponse.questions().size());
                            
                            // 2. 필터링된 문제만 PostgreSQL에 저장
                            return questionHistoryService.saveQuestions(topic, filteredQuestions)
                                    .doOnNext(savedCount -> log.info("PostgreSQL 저장 문제 수: {}", savedCount))
                                    // 3. Elasticsearch에도 인덱싱
                                    .then(indexQuestionsToES(topic, filteredQuestions))
                                    .thenReturn(new QuizResponse(quizResponse.quizTitle(), filteredQuestions));
                        })
                );
    }

    /**
     * ES 유사도 기반으로 중복 질문 필터링
     */
    private Mono<List<Question>> filterDuplicateQuestions(List<Question> questions) {
        return reactor.core.publisher.Flux.fromIterable(questions)
                .filterWhen(q -> questionSearchService.checkDuplicate(q.questionText())
                        .map(result -> !result.dup())
                        .onErrorReturn(true)) // ES 연결 실패시 통과
                .collectList();
    }

    /**
     * Elasticsearch에 질문 인덱싱
     */
    private Mono<Void> indexQuestionsToES(String topic, List<Question> questions) {
        return reactor.core.publisher.Flux.fromIterable(questions)
                .flatMap(q -> questionSearchService.index(topic, q.questionText(), 
                        q.options(), q.correctAnswer(), q.explanation())
                        .onErrorResume(e -> {
                            log.warn("ES 인덱싱 실패: {}", e.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    @Operation(summary = "개발용 더미 퀴즈 생성", description = "실제 AI 호출 없이 더미 데이터로 퀴즈를 생성합니다. 개발 및 테스트용으로 사용됩니다.")
    @ApiResponse(responseCode = "200", description = "더미 퀴즈 생성 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuizResponse.class)))
    @PostMapping("/dummy")
    public Mono<QuizResponse> createDummyQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @RequestBody QuizRequest request) {
        int requested = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        int questionsToGenerate = Math.max(1, Math.min(20, requested));
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

    @Operation(summary = "퀴즈 결과 마크다운 렌더링", description = "LLM 호출 없이 백엔드에서 직접 마크다운을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "로컬 마크다운 렌더링 성공",
            content = @Content(mediaType = "text/markdown", schema = @Schema(implementation = String.class)))
    @PostMapping(value = "/markdown", consumes = "application/json", produces = "text/markdown;charset=UTF-8")
    public Mono<String> renderMarkdown(@RequestBody Map<String, Object> payload) {
        return geminiService.renderQuizMarkdownLocally(payload);
    }
}
