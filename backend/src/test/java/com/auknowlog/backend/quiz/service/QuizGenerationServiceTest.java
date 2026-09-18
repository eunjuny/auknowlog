package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.embedding.service.SemanticDuplicateService;
import com.auknowlog.backend.embedding.service.EmbeddingResult;
import com.auknowlog.backend.common.observability.QuizGenerationMetrics;
import com.auknowlog.backend.feedback.service.QuestionFeedbackService;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizObjectiveAllocation;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.source.service.SourceService;
import com.auknowlog.backend.roadmap.service.RoadmapQuizPlanningService;
import com.auknowlog.backend.quiz.dto.RoadmapQuizPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizGenerationServiceTest {

    @Mock
    private OpenAiQuizService openAiQuizService;

    @Mock
    private QuestionHistoryService questionHistoryService;

    @Mock
    private SemanticDuplicateService semanticDuplicateService;

    @Mock
    private QuestionFeedbackService questionFeedbackService;

    @Mock
    private LearningService learningService;

    @Mock
    private RoadmapQuizPlanningService roadmapQuizPlanningService;

    @Mock
    private SourceService sourceService;

    @Mock
    private LangfuseTracingService langfuseTracingService;

    @Mock
    private QuizGenerationMetrics quizGenerationMetrics;

    @InjectMocks
    private QuizGenerationService quizGenerationService;

    @BeforeEach
    void setUpTracing() {
        when(roadmapQuizPlanningService.plan(any())).thenAnswer(invocation -> {
            QuizRequest request = invocation.getArgument(0);
            return RoadmapQuizPlan.standard(request.sourceId(),
                    request.numberOfQuestions() == null ? 5 : request.numberOfQuestions());
        });
        when(langfuseTracingService.startQuizGeneration(any(), anyInt(), anyBoolean()))
                .thenReturn(LangfuseTracingService.noopScope());
        when(langfuseTracingService.startOperation(any(), ArgumentMatchers.anyMap()))
                .thenReturn(LangfuseTracingService.noopScope());
        when(questionFeedbackService.getSimilarityAvoidanceQuestions(any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void savesAndIndexesOnlyNewQuestions() {
        Question question = new Question(
                "Java의 JVM은 어떤 역할을 하나요?",
                List.of("바이트코드 실행", "이미지 편집", "문서 작성", "네트워크 차단"),
                "바이트코드 실행",
                "JVM은 Java 바이트코드를 실행합니다."
        );
        QuizResponse modelResponse = new QuizResponse("Java 기초 퀴즈", List.of(question));

        when(questionHistoryService.getRecentQuestionPreviews("Java", 30)).thenReturn(List.of());
        when(openAiQuizService.generateQuiz(eq("Java"), eq(1), ArgumentMatchers.anyList(), ArgumentMatchers.anyList()))
                .thenReturn(modelResponse);
        when(questionHistoryService.isDuplicate(question.questionText())).thenReturn(false);
        when(semanticDuplicateService.check(question.questionText()))
                .thenReturn(new SemanticDuplicateService.SemanticCheck(false, 0, Optional.empty()));
        when(questionHistoryService.saveQuestion("Java", question)).thenReturn(true);
        when(learningService.storeGeneratedQuiz(eq("Java"), eq(null), eq(null), eq(null),
                ArgumentMatchers.any(QuizResponse.class)))
                .thenAnswer(invocation -> ((QuizResponse) invocation.getArgument(4)).withQuizId(7L));

        QuizResponse response = quizGenerationService.createQuiz(new QuizRequest(" Java ", 1));

        assertThat(response.quizId()).isEqualTo(7L);
        assertThat(response.quizTitle()).isEqualTo("Java 기초 퀴즈");
        assertThat(response.questions()).containsExactly(question);
        verify(questionHistoryService).saveQuestion("Java", question);
        verify(semanticDuplicateService).indexSavedQuestion(question.questionText(),
                new SemanticDuplicateService.SemanticCheck(false, 0, Optional.empty()));
    }

    @Test
    void keepsExactHashProtectionWhenEmbeddingCheckFails() {
        Question question = new Question(
                "Spring의 의존성 주입은 무엇인가요?",
                List.of("객체 의존성을 외부에서 제공", "SQL 자동 생성", "화면 렌더링", "파일 압축"),
                "객체 의존성을 외부에서 제공",
                "의존성 주입은 객체가 필요한 의존성을 외부에서 전달받게 합니다."
        );
        QuizResponse modelResponse = new QuizResponse("Spring 기초 퀴즈", List.of(question));
        QuestionHistoryRepository exactHashRepository = mock(QuestionHistoryRepository.class);
        QuestionHistoryService exactHashService = new QuestionHistoryService(exactHashRepository, new ObjectMapper());
        QuizGenerationService serviceWithRealHashFallback = new QuizGenerationService(
                openAiQuizService,
                exactHashService,
                semanticDuplicateService,
                questionFeedbackService,
                sourceService,
                learningService,
                roadmapQuizPlanningService,
                langfuseTracingService,
                quizGenerationMetrics
        );

        when(exactHashRepository.findByTopic("Spring")).thenReturn(List.of());
        when(openAiQuizService.generateQuiz(eq("Spring"), eq(1), ArgumentMatchers.anyList(), ArgumentMatchers.anyList()))
                .thenReturn(modelResponse);
        when(semanticDuplicateService.check(question.questionText()))
                .thenThrow(new IllegalStateException("embedding service unavailable"));
        when(learningService.storeGeneratedQuiz(eq("Spring"), eq(null), eq(null), eq(null),
                ArgumentMatchers.any(QuizResponse.class)))
                .thenAnswer(invocation -> ((QuizResponse) invocation.getArgument(4)).withQuizId(8L));

        QuizResponse response = serviceWithRealHashFallback.createQuiz(new QuizRequest("Spring", 1));

        assertThat(response.quizId()).isEqualTo(8L);
        assertThat(response.questions()).containsExactly(question);
        String expectedHash = exactHashService.generateHash(question.questionText());
        verify(exactHashRepository, times(2)).existsByQuestionHash(expectedHash);
        verify(exactHashRepository).save(ArgumentMatchers.argThat(
                savedQuestion -> expectedHash.equals(savedQuestion.getQuestionHash())));
        verify(semanticDuplicateService).indexSavedQuestion(
                question.questionText(), SemanticDuplicateService.SemanticCheck.notAvailable());
    }

    @Test
    void prioritizesSimilarityFeedbackInPromptAndAppliesLowerSemanticThreshold() {
        String feedbackQuestion = "JVM이 바이트코드를 실행하는 이유는 무엇인가요?";
        Question repetitive = new Question(
                "JVM의 바이트코드 실행 역할은 무엇인가요?",
                List.of("실행", "압축", "전송", "복제"),
                "실행",
                "JVM은 바이트코드를 실행합니다."
        );
        Question novel = new Question(
                "JIT 컴파일이 자주 실행되는 코드를 최적화하는 방식은 무엇인가요?",
                List.of("런타임 컴파일", "파일 압축", "DNS 조회", "메시지 복제"),
                "런타임 컴파일",
                "JIT는 런타임에 자주 실행되는 코드를 컴파일합니다."
        );
        SemanticDuplicateService.SemanticCheck standardCheck = new SemanticDuplicateService.SemanticCheck(
                false, 0, Optional.of(new EmbeddingResult("fixture", new float[512], 0)));
        SemanticDuplicateService.SemanticCheck feedbackDuplicate = new SemanticDuplicateService.SemanticCheck(
                true, 0.85, standardCheck.embedding());

        when(questionFeedbackService.getSimilarityAvoidanceQuestions("Java", 10))
                .thenReturn(List.of(feedbackQuestion));
        when(questionHistoryService.generateHash(feedbackQuestion)).thenReturn("f".repeat(64));
        when(questionHistoryService.getRecentQuestionPreviews("Java", 30)).thenReturn(List.of("최근 Java 문제"));
        when(openAiQuizService.generateQuiz(eq("Java"), eq(1), ArgumentMatchers.anyList(), ArgumentMatchers.anyList()))
                .thenReturn(new QuizResponse("Java 퀴즈", List.of(repetitive)));
        when(openAiQuizService.generateQuiz(eq("Java"), eq(2), ArgumentMatchers.anyList(), ArgumentMatchers.anyList()))
                .thenReturn(new QuizResponse("Java 퀴즈", List.of(novel)));
        when(questionHistoryService.isDuplicate(any())).thenReturn(false);
        when(semanticDuplicateService.check(any())).thenReturn(standardCheck);
        when(semanticDuplicateService.checkAgainstQuestionHashes(
                eq(standardCheck), eq(List.of("f".repeat(64))), eq(0.82)))
                .thenReturn(feedbackDuplicate, standardCheck);
        when(questionHistoryService.saveQuestion("Java", novel)).thenReturn(true);
        when(learningService.storeGeneratedQuiz(eq("Java"), eq(null), eq(null), eq(null),
                ArgumentMatchers.any(QuizResponse.class)))
                .thenAnswer(invocation -> ((QuizResponse) invocation.getArgument(4)).withQuizId(9L));

        QuizResponse response = quizGenerationService.createQuiz(new QuizRequest("Java", 1));

        assertThat(response.questions()).containsExactly(novel);
        verify(openAiQuizService).generateQuiz(
                eq("Java"), eq(1),
                ArgumentMatchers.argThat(questions -> questions.getFirst().startsWith("JVM이 바이트코드")),
                ArgumentMatchers.anyList());
        verify(questionHistoryService).saveQuestion("Java", novel);
    }

    @Test
    void generatesAndStoresQuestionsAccordingToTheRoadmapObjectivePlan() {
        QuizRequest request = new QuizRequest("Kubernetes Pod", 2, null, 3L, 7L);
        List<QuizObjectiveAllocation> allocations = List.of(
                new QuizObjectiveAllocation(101L, "lifecycle", "Pod 생명주기", "상태 전이", "CORE", 1),
                new QuizObjectiveAllocation(102L, "probe", "상태 프로브", "프로브 역할", "CORE", 1)
        );
        Question lifecycle = new Question("Pending 상태는?", List.of("대기", "완료", "삭제", "종료"),
                "대기", "스케줄링을 기다립니다.", List.of("source-44-chunk-1"), "lifecycle");
        Question probe = new Question("readiness 실패는?", List.of("트래픽 제외", "노드 종료", "이미지 삭제", "DNS 삭제"),
                "트래픽 제외", "Service 대상에서 제외됩니다.", List.of("source-44-chunk-1"), "probe");

        when(roadmapQuizPlanningService.plan(request)).thenReturn(new RoadmapQuizPlan(44L, 2, allocations));
        when(sourceService.getQuizContext(44L, "Kubernetes Pod")).thenReturn(List.of());
        when(questionHistoryService.getRecentQuestionPreviews("Kubernetes Pod", 30)).thenReturn(List.of());
        when(openAiQuizService.generateQuiz(eq("Kubernetes Pod"), eq(2), ArgumentMatchers.anyList(),
                ArgumentMatchers.anyList(), eq(allocations)))
                .thenReturn(new QuizResponse("Pod 핵심 퀴즈", List.of(lifecycle, probe)));
        when(questionHistoryService.isDuplicate(any())).thenReturn(false);
        when(semanticDuplicateService.check(any())).thenReturn(SemanticDuplicateService.SemanticCheck.notAvailable());
        when(questionHistoryService.saveQuestion(eq("Kubernetes Pod"), any())).thenReturn(true);
        when(learningService.storeGeneratedQuiz(eq("Kubernetes Pod"), eq(44L), eq(3L), eq(7L),
                ArgumentMatchers.any(QuizResponse.class)))
                .thenAnswer(invocation -> ((QuizResponse) invocation.getArgument(4)).withQuizId(10L));

        QuizResponse response = quizGenerationService.createQuiz(request);

        assertThat(response.questions()).extracting(Question::objectiveKey)
                .containsExactly("lifecycle", "probe");
        verify(learningService).storeGeneratedQuiz(eq("Kubernetes Pod"), eq(44L), eq(3L), eq(7L),
                ArgumentMatchers.argThat(quiz -> quiz.questions().size() == 2));
    }
}
