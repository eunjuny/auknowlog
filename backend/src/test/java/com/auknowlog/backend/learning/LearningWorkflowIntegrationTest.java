package com.auknowlog.backend.learning;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import com.auknowlog.backend.feedback.repository.QuestionFeedbackRepository;
import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.learning.repository.ReviewScheduleRepository;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        // V3은 H2가 지원하지 않는 pgvector 확장이다. V4~V8 스키마는 H2 전용 보조 스크립트로 검증한다.
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:h2-learning-schema.sql",
        "spring.jpa.hibernate.ddl-auto=validate",
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

    @Autowired
    private AiGenerationLogRepository aiGenerationLogRepository;

    @Autowired
    private QuestionFeedbackRepository questionFeedbackRepository;

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
        String attemptResponse = mockMvc.perform(post("/api/learning-attempts")
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
                .andExpect(jsonPath("$.reviewScheduledCount").value(1))
                .andReturn().getResponse().getContentAsString();

        long attemptId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(attemptResponse).path("attemptId").asLong();

        // 자동 저장 요청이 응답 유실 등으로 재시도돼도 한 퀴즈의 풀이 기록과 복습 일정은 중복 생성하지 않는다.
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
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.reviewScheduledCount").value(1));

        mockMvc.perform(get("/api/learning-attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.attempts[0].attemptId").value(attemptId))
                .andExpect(jsonPath("$.attempts[0].topic").value("Java"))
                .andExpect(jsonPath("$.attempts[0].correctAnswers").value(1));

        mockMvc.perform(get("/api/learning-attempts/{attemptId}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].questionOrder").value(1))
                .andExpect(jsonPath("$.questions[0].selectedAnswer").value("선택지 B"))
                .andExpect(jsonPath("$.questions[0].correct").value(false))
                .andExpect(jsonPath("$.questions[1].correct").value(true));

        String feedbackResponse = mockMvc.perform(put("/api/question-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quizId": %d,
                                  "questionOrder": 1,
                                  "feedbackType": "AMBIGUOUS",
                                  "comment": "정답 조건을 더 분명히 설명해주세요."
                                }
                                """.formatted(quizId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionOrder").value(1))
                .andExpect(jsonPath("$.feedbackType").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.feedbackTypeLabel").value("질문이 모호해요"))
                .andExpect(jsonPath("$.updated").value(false))
                .andReturn().getResponse().getContentAsString();

        long feedbackId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(feedbackResponse).path("feedbackId").asLong();

        // 현재 단일 사용자 화면에서는 같은 문항의 피드백을 다시 저장하면 새 행을 만들지 않고 최신 의견으로 갱신한다.
        mockMvc.perform(put("/api/question-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quizId": %d,
                                  "questionOrder": 1,
                                  "feedbackType": "EXPLANATION_INSUFFICIENT",
                                  "comment": "해설에 오답 선택지의 차이도 넣어주세요."
                                }
                                """.formatted(quizId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(feedbackId))
                .andExpect(jsonPath("$.feedbackType").value("EXPLANATION_INSUFFICIENT"))
                .andExpect(jsonPath("$.updated").value(true));

        aiGenerationLogRepository.save(new AiGenerationLog(
                "QUIZ_GENERATION", "gpt-5.4-mini", "SUCCESS", 100L, 20L, 120L, 450L, null
        ));
        aiGenerationLogRepository.save(new AiGenerationLog(
                "QUIZ_GENERATION", "gpt-5.4-mini", "FAILED", null, null, null, 1_000L, "unavailable"
        ));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learning.totalAttempts").value(1))
                .andExpect(jsonPath("$.learning.totalQuestions").value(2))
                .andExpect(jsonPath("$.learning.correctAnswers").value(1))
                .andExpect(jsonPath("$.learning.accuracyPercent").value(50))
                .andExpect(jsonPath("$.learning.topicAccuracy[0].topic").value("Java"))
                .andExpect(jsonPath("$.learning.recommendations[0].topic").value("Java"))
                .andExpect(jsonPath("$.learning.recommendations[0].priority").value("WEAKNESS"))
                .andExpect(jsonPath("$.learning.recommendations[0].recommendedQuestionCount").value(4))
                .andExpect(jsonPath("$.ai.totalCalls").value(2))
                .andExpect(jsonPath("$.ai.successfulCalls").value(1))
                .andExpect(jsonPath("$.ai.failedCalls").value(1))
                .andExpect(jsonPath("$.ai.totalTokens").value(120))
                .andExpect(jsonPath("$.ai.successRatePercent").value(50))
                .andExpect(jsonPath("$.ai.modelUsage[0].model").value("gpt-5.4-mini"))
                .andExpect(jsonPath("$.qualityFeedback.totalFeedbackCount").value(1))
                .andExpect(jsonPath("$.qualityFeedback.openFeedbackCount").value(1))
                .andExpect(jsonPath("$.qualityFeedback.typeMetrics[0].type").value("EXPLANATION_INSUFFICIENT"));

        String roadmapResponse = mockMvc.perform(post("/api/learning-roadmaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Java",
                                  "durationWeeks": 2,
                                  "questionsPerWeek": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Java"))
                .andExpect(jsonPath("$.durationWeeks").value(2))
                .andExpect(jsonPath("$.totalPlannedQuestions").value(4))
                .andExpect(jsonPath("$.completedQuestions").value(0))
                .andReturn().getResponse().getContentAsString();

        long roadmapId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(roadmapResponse).path("roadmapId").asLong();

        // 같은 Java 주제라도 로드맵 생성 전에 푼 문제는 진행률에 포함하지 않는다.
        mockMvc.perform(get("/api/learning-roadmaps/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadmap.roadmapId").value(roadmapId))
                .andExpect(jsonPath("$.roadmap.completedQuestions").value(0));

        String roadmapQuizResponse = mockMvc.perform(post("/api/quizzes/dummy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Java",
                                  "numberOfQuestions": 2,
                                  "roadmapId": %d
                                }
                                """.formatted(roadmapId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long roadmapQuizId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(roadmapQuizResponse).path("quizId").asLong();

        mockMvc.perform(post("/api/learning-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quizId": %d,
                                  "answers": [
                                    {"questionOrder": 1, "selectedAnswer": "선택지 A"},
                                    {"questionOrder": 2, "selectedAnswer": "선택지 A"}
                                  ]
                                }
                                """.formatted(roadmapQuizId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctAnswers").value(2));

        mockMvc.perform(get("/api/learning-roadmaps/{roadmapId}", roadmapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedQuestions").value(2))
                .andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.weeks[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.weeks[0].completedQuestions").value(2));

        String roadmapJson = """
                {
                  "version": "1.0",
                  "title": "Kubernetes 단계별 학습",
                  "topic": "Kubernetes",
                  "description": "개념을 익힌 뒤 네트워크로 진행합니다.",
                  "durationWeeks": 4,
                  "steps": [
                    {
                      "key": "core",
                      "title": "핵심 개념",
                      "description": "Pod와 Deployment를 학습합니다.",
                      "topic": "Kubernetes 핵심 개념",
                      "questionTarget": 2,
                      "dependsOn": []
                    },
                    {
                      "key": "network",
                      "title": "네트워크",
                      "description": "Service와 Ingress를 학습합니다.",
                      "topic": "Kubernetes 네트워크",
                      "questionTarget": 2,
                      "dependsOn": ["core"]
                    }
                  ]
                }
                """;
        MockMultipartFile roadmapFile = new MockMultipartFile(
                "file", "kubernetes.roadmap.json", MediaType.APPLICATION_JSON_VALUE,
                roadmapJson.getBytes(StandardCharsets.UTF_8));
        String importedRoadmapResponse = mockMvc.perform(multipart("/api/learning-roadmaps/import").file(roadmapFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("FILE_IMPORT"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].key").value("core"))
                .andExpect(jsonPath("$.steps[0].status").value("READY"))
                .andExpect(jsonPath("$.steps[1].status").value("LOCKED"))
                .andExpect(jsonPath("$.steps[1].prerequisiteKeys[0]").value("core"))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode importedRoadmap = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(importedRoadmapResponse);
        long importedRoadmapId = importedRoadmap.path("roadmapId").asLong();
        long coreStepId = importedRoadmap.path("steps").get(0).path("stepId").asLong();
        long networkStepId = importedRoadmap.path("steps").get(1).path("stepId").asLong();

        // UI 버튼뿐 아니라 API에서도 잠긴 단계를 우회해 시작할 수 없다.
        mockMvc.perform(post("/api/quizzes/dummy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Kubernetes 네트워크",
                                  "numberOfQuestions": 2,
                                  "roadmapId": %d,
                                  "roadmapStepId": %d
                                }
                                """.formatted(importedRoadmapId, networkStepId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("선행 학습 단계를 먼저 완료해주세요."));

        String stageQuizResponse = mockMvc.perform(post("/api/quizzes/dummy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "Kubernetes 핵심 개념",
                                  "numberOfQuestions": 2,
                                  "roadmapId": %d,
                                  "roadmapStepId": %d
                                }
                                """.formatted(importedRoadmapId, coreStepId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long stageQuizId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(stageQuizResponse).path("quizId").asLong();

        mockMvc.perform(post("/api/learning-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quizId": %d,
                                  "answers": [
                                    {"questionOrder": 1, "selectedAnswer": "선택지 A"},
                                    {"questionOrder": 2, "selectedAnswer": "선택지 A"}
                                  ]
                                }
                                """.formatted(stageQuizId)))
                .andExpect(status().isOk());

        // 선행 단계 목표를 채우면 다음 단계가 자동으로 잠금 해제된다.
        mockMvc.perform(get("/api/learning-roadmaps/{roadmapId}", importedRoadmapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.steps[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps[1].status").value("READY"))
                .andExpect(jsonPath("$.currentStepKey").value("network"));

        MockMultipartFile invalidExtension = new MockMultipartFile(
                "file", "kubernetes.json", MediaType.APPLICATION_JSON_VALUE,
                roadmapJson.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/learning-roadmaps/import").file(invalidExtension))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("로드맵 파일은 .roadmap.json 확장자만 가져올 수 있습니다."));

        MockMultipartFile cyclicRoadmap = new MockMultipartFile(
                "file", "cycle.roadmap.json", MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "version":"1.0",
                          "title":"순환 계획",
                          "topic":"테스트",
                          "durationWeeks":2,
                          "steps":[
                            {"key":"a","title":"A","questionTarget":1,"dependsOn":["b"]},
                            {"key":"b","title":"B","questionTarget":1,"dependsOn":["a"]}
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/learning-roadmaps/import").file(cyclicRoadmap))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("선행 단계에 순환 참조가 있습니다."));

        assertThat(sourceDocumentRepository.count()).isEqualTo(1);
        assertThat(learningAttemptRepository.count()).isEqualTo(3);
        assertThat(reviewScheduleRepository.count()).isEqualTo(1);
        assertThat(questionFeedbackRepository.count()).isEqualTo(1);
    }
}
