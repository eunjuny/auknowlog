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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "Quiz API", description = "퀴즈 생성 및 관리를 위한 API")
@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private static final Logger log = LoggerFactory.getLogger(QuizController.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;  // 최대 재시도 횟수

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
    public QuizResponse createQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @RequestBody QuizRequest request) {
        int targetCount = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        targetCount = Math.max(1, Math.min(20, targetCount));
        String topic = request.topic();

        List<Question> collectedQuestions = new ArrayList<>();
        String quizTitle = topic + " 퀴즈";
        int attempts = 0;

        // 원하는 개수가 될 때까지 반복 생성 (최대 MAX_RETRY_ATTEMPTS번)
        while (collectedQuestions.size() < targetCount && attempts < MAX_RETRY_ATTEMPTS) {
            int remaining = targetCount - collectedQuestions.size();
            // 중복 필터링을 고려해서 여유있게 요청 (첫 시도는 그대로, 재시도는 2배)
            int toGenerate = (attempts == 0) ? remaining : Math.min(remaining * 2, 20);
            
            log.info("🔄 퀴즈 생성 시도 {}/{}: 필요 {}개, 요청 {}개", 
                    attempts + 1, MAX_RETRY_ATTEMPTS, remaining, toGenerate);

            try {
                // 첫 시도에만 기존 문제 목록 조회 (토큰 절약)
                List<String> existingQuestions = (attempts == 0) 
                        ? questionHistoryService.getRecentQuestionPreviews(topic, 30)
                        : List.of();
                
                QuizResponse response = geminiService.generateQuiz(topic, toGenerate, existingQuestions);
                if (attempts == 0) {
                    quizTitle = response.quizTitle();
                }

                // 중복 체크 후 필터링
                List<Question> filtered = filterDuplicateQuestions(response.questions());
                log.info("📊 생성 {}개 → 필터링 후 {}개", response.questions().size(), filtered.size());

                // 새 문제들을 즉시 저장 (다음 루프에서 중복 체크에 반영되도록)
                for (Question q : filtered) {
                    if (collectedQuestions.size() >= targetCount) break;
                    
                    // PostgreSQL 저장
                    questionHistoryService.saveQuestion(topic, q);
                    // ES 인덱싱
                    indexQuestionToES(topic, q);
                    
                    collectedQuestions.add(q);
                }
            } catch (Exception e) {
                log.warn("⚠️ 퀴즈 생성 실패 (시도 {}): {}", attempts + 1, e.getMessage());
            }

            attempts++;
        }

        log.info("✅ 최종 퀴즈: 요청 {}개 → 생성 {}개 (시도 {}회)", 
                targetCount, collectedQuestions.size(), attempts);

        return new QuizResponse(quizTitle, collectedQuestions);
    }

    /**
     * 단일 문제 ES 인덱싱
     */
    private void indexQuestionToES(String topic, Question q) {
        try {
            questionSearchService.index(topic, q.questionText(), 
                    q.options(), q.correctAnswer(), q.explanation());
        } catch (Exception e) {
            log.warn("ES 인덱싱 실패: {}", e.getMessage());
        }
    }

    /**
     * ES 유사도 기반으로 중복 질문 필터링
     */
    private List<Question> filterDuplicateQuestions(List<Question> questions) {
        List<Question> result = new ArrayList<>();
        for (Question q : questions) {
            try {
                var checkResult = questionSearchService.checkDuplicate(q.questionText());
                if (checkResult.dup()) {
                    log.info("🚫 중복 문제 필터링: score={}, msg={}, question={}", 
                            checkResult.score(), checkResult.msg(), 
                            q.questionText().substring(0, Math.min(50, q.questionText().length())));
                } else {
                    log.debug("✅ 새 문제: {}", q.questionText().substring(0, Math.min(50, q.questionText().length())));
                    result.add(q);
                }
            } catch (Exception e) {
                log.warn("⚠️ ES 체크 실패, 문제 통과: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                result.add(q);
            }
        }
        return result;
    }

    /**
     * Elasticsearch에 질문 인덱싱
     */
    private void indexQuestionsToES(String topic, List<Question> questions) {
        for (Question q : questions) {
            try {
                questionSearchService.index(topic, q.questionText(), 
                        q.options(), q.correctAnswer(), q.explanation());
            } catch (Exception e) {
                log.warn("ES 인덱싱 실패: {}", e.getMessage());
            }
        }
    }

    @Operation(summary = "개발용 더미 퀴즈 생성", description = "실제 AI 호출 없이 더미 데이터로 퀴즈를 생성합니다.")
    @ApiResponse(responseCode = "200", description = "더미 퀴즈 생성 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuizResponse.class)))
    @PostMapping("/dummy")
    public QuizResponse createDummyQuiz(
            @Parameter(description = "퀴즈 생성 요청 객체 (주제 및 문제 수 포함)", required = true)
            @RequestBody QuizRequest request) {
        int requested = (request.numberOfQuestions() != null) ? request.numberOfQuestions() : 10;
        int questionsToGenerate = Math.max(1, Math.min(20, requested));
        return createDummyQuizResponse(request.topic(), questionsToGenerate);
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
    public String renderMarkdown(@RequestBody Map<String, Object> payload) {
        return geminiService.renderQuizMarkdownLocally(payload);
    }
}
