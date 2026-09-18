package com.auknowlog.backend.quiz.controller;

import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.quiz.dto.QuizViewResponse;
import com.auknowlog.backend.quiz.dto.RoadmapQuizPlan;
import com.auknowlog.backend.quiz.service.OpenAiQuizService;
import com.auknowlog.backend.quiz.service.QuizOptionOrderService;
import com.auknowlog.backend.quiz.service.QuizGenerationService;
import com.auknowlog.backend.roadmap.service.RoadmapQuizPlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Quiz API", description = "퀴즈 생성 및 관리를 위한 API")
@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final OpenAiQuizService openAiQuizService;
    private final QuizGenerationService quizGenerationService;
    private final LearningService learningService;
    private final RoadmapQuizPlanningService roadmapQuizPlanningService;

    public QuizController(OpenAiQuizService openAiQuizService,
                          QuizGenerationService quizGenerationService,
                          LearningService learningService,
                          RoadmapQuizPlanningService roadmapQuizPlanningService) {
        this.openAiQuizService = openAiQuizService;
        this.quizGenerationService = quizGenerationService;
        this.learningService = learningService;
        this.roadmapQuizPlanningService = roadmapQuizPlanningService;
    }

    @Operation(summary = "새로운 퀴즈 생성", description = "주제와 문제 수를 기반으로 OpenAI를 통해 새로운 객관식 퀴즈를 생성합니다.")
    @ApiResponse(responseCode = "200", description = "퀴즈 생성 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuizViewResponse.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터",
            content = @Content(mediaType = "application/json"))
    @PostMapping("/create")
    public QuizViewResponse createQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @Valid @RequestBody QuizRequest request) {
        return QuizViewResponse.from(quizGenerationService.createQuiz(request));
    }

    @Operation(summary = "개발용 더미 퀴즈 생성", description = "실제 AI 호출 없이 더미 데이터로 퀴즈를 생성합니다.")
    @ApiResponse(responseCode = "200", description = "더미 퀴즈 생성 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuizViewResponse.class)))
    @PostMapping("/dummy")
    public QuizViewResponse createDummyQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @Valid @RequestBody QuizRequest request) {
        RoadmapQuizPlan plan = roadmapQuizPlanningService.plan(request);
        QuizResponse response = createDummyQuizResponse(request.topic(), plan);
        QuizResponse storedQuiz = learningService.storeGeneratedQuiz(request.topic().trim(), plan.sourceId(), request.roadmapId(),
                request.roadmapStepId(), response);
        return QuizViewResponse.from(storedQuiz);
    }

    private QuizResponse createDummyQuizResponse(String topic, RoadmapQuizPlan plan) {
        String quizTitle = topic != null ? topic + " 퀴즈" : "더미 퀴즈";
        List<String> objectiveKeys = plan.objectiveAllocations().stream()
                .flatMap(allocation -> java.util.stream.IntStream.range(0, allocation.questionCount())
                        .mapToObj(ignored -> allocation.key()))
                .toList();

        List<Question> questions = new ArrayList<>();
        for (int i = 1; i <= plan.questionCount(); i++) {
            questions.add(new Question(
                "더미 문제 " + i + ": " + topic + "에 대한 질문입니다.",
                List.of("선택지 A", "선택지 B", "선택지 C", "선택지 D"),
                "선택지 A",
                "이것은 더미 데이터로 생성된 문제입니다. 정답은 선택지 A입니다.",
                List.of(),
                objectiveKeys.isEmpty() ? null : objectiveKeys.get(i - 1)
            ));
        }

        return QuizOptionOrderService.shuffleOptionsIndependently(new QuizResponse(quizTitle, questions));
    }

    @Operation(summary = "퀴즈 결과 마크다운 렌더링", description = "LLM 호출 없이 백엔드에서 직접 마크다운을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "로컬 마크다운 렌더링 성공",
            content = @Content(mediaType = "text/markdown", schema = @Schema(implementation = String.class)))
    @PostMapping(value = "/markdown", consumes = "application/json", produces = "text/markdown;charset=UTF-8")
    public String renderMarkdown(@RequestBody Map<String, Object> payload) {
        return openAiQuizService.renderQuizMarkdownLocally(payload);
    }
}
