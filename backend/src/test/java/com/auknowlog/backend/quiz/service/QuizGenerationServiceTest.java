package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.embedding.service.SemanticDuplicateService;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.source.service.SourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private QuizGenerationService quizGenerationService;

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
        when(learningService.storeGeneratedQuiz(eq("Java"), eq(null), ArgumentMatchers.any(QuizResponse.class)))
                .thenAnswer(invocation -> ((QuizResponse) invocation.getArgument(2)).withQuizId(7L));

        QuizResponse response = quizGenerationService.createQuiz(new QuizRequest(" Java ", 1));

        assertThat(response.quizId()).isEqualTo(7L);
        assertThat(response.quizTitle()).isEqualTo("Java 기초 퀴즈");
        assertThat(response.questions()).containsExactly(question);
        verify(questionHistoryService).saveQuestion("Java", question);
        verify(semanticDuplicateService).indexSavedQuestion(question.questionText(),
                new SemanticDuplicateService.SemanticCheck(false, 0, Optional.empty()));
    }
}
