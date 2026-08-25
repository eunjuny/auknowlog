package com.auknowlog.backend.quiz.controller;

import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.quiz.service.OpenAiQuizService;
import com.auknowlog.backend.quiz.service.QuizGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuizController.class)
class QuizControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenAiQuizService openAiQuizService;

    @MockitoBean
    private QuizGenerationService quizGenerationService;

    @MockitoBean
    private LearningService learningService;

    @Test
    void rejectsBlankTopicsBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post("/api/quizzes/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"   \",\"numberOfQuestions\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("학습 주제를 입력해주세요."));
    }
}
