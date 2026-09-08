package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.embedding.service.SemanticDuplicateService;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.source.service.SourceService;
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
    private LearningService learningService;

    @Mock
    private SourceService sourceService;

    @Mock
    private LangfuseTracingService langfuseTracingService;

    @InjectMocks
    private QuizGenerationService quizGenerationService;

    @BeforeEach
    void setUpTracing() {
        when(langfuseTracingService.startQuizGeneration(any(), anyInt(), anyBoolean()))
                .thenReturn(LangfuseTracingService.noopScope());
        when(langfuseTracingService.startOperation(any(), ArgumentMatchers.anyMap()))
                .thenReturn(LangfuseTracingService.noopScope());
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
                sourceService,
                learningService,
                langfuseTracingService
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
}
