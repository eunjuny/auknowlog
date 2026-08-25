package com.auknowlog.backend.learning;

import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.learning.repository.ReviewScheduleRepository;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 외부 모델을 호출하지 않는 실제 샘플 흐름이다.
 * H2에서는 V3(pgvector) 이전 스키마까지 검증하며, V3는 Docker/Testcontainers 환경에서 추가 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:learning_workflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.target=2",
        "auknowlog.openai.embedding.enabled=false"
})
@AutoConfigureMockMvc
class LearningWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceDocumentRepository sourceDocumentRepository;

    @Autowired
    private LearningAttemptRepository learningAttemptRepository;

    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;

    @Test
    void savesSourceGeneratesCostFreeDemoQuizAndSchedulesWrongAnswerReview() throws Exception {
        String sourceBody = """
                {
                  "title": "Java Virtual Machine 개요",
                  "content": "JVM은 Java 바이트코드를 실행합니다. JIT 컴파일러는 자주 실행되는 코드를 최적화합니다."
                }
                """;

        String sourceResponse = mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andReturn().getResponse().getContentAsString();

        long sourceId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(sourceResponse).path("sourceId").asLong();
        String quizResponse = mockMvc.perform(post("/api/quizzes/dummy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Java\",\"numberOfQuestions\":2,\"sourceId\":" + sourceId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").isNumber())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        long quizId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(quizResponse).path("quizId").asLong();
        mockMvc.perform(post("/api/learning-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quizId": %d,
                                  "answers": [
                                    {"questionOrder": 1, "selectedAnswer": "선택지 B"},
                                    {"questionOrder": 2, "selectedAnswer": "선택지 A"}
                                  ]
                                }
                                """.formatted(quizId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.correctAnswers").value(1))
                .andExpect(jsonPath("$.wrongAnswers").value(1))
                .andExpect(jsonPath("$.reviewScheduledCount").value(1));

        assertThat(sourceDocumentRepository.count()).isEqualTo(1);
        assertThat(learningAttemptRepository.count()).isEqualTo(1);
        assertThat(reviewScheduleRepository.count()).isEqualTo(1);
    }
}
