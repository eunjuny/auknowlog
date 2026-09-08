package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.ai.service.AiGenerationLedgerService;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.common.observability.AiGenerationMetrics;
import com.auknowlog.backend.observability.LangfuseTracingService;
import com.auknowlog.backend.source.dto.SourceChunkContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiQuizServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiQuizService service;
    private SimpleMeterRegistry meterRegistry;
    private AiGenerationLedgerService aiGenerationLedgerService;
    private LangfuseTracingService langfuseTracingService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        meterRegistry = new SimpleMeterRegistry();
        aiGenerationLedgerService = mock(AiGenerationLedgerService.class);
        langfuseTracingService = mock(LangfuseTracingService.class);
        when(langfuseTracingService.startGeneration(any(), any(), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(LangfuseTracingService.noopScope());
        service = new OpenAiQuizService(builder, objectMapper, new AiGenerationMetrics(meterRegistry), aiGenerationLedgerService, langfuseTracingService);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.openai.com/v1/responses");
        ReflectionTestUtils.setField(service, "modelName", "gpt-5.4-mini");
        ReflectionTestUtils.setField(service, "reasoningEffort", "low");
    }

    @Test
    void generatesQuizUsingResponsesApiAndStructuredOutput() throws Exception {
        String quizJson = objectMapper.writeValueAsString(new QuizResponse("Java 기초 퀴즈", List.of(
                new Question("Java의 특징은 무엇인가요?", List.of("객체지향", "브라우저", "문서", "운영체제"), "객체지향", "Java는 객체지향 언어입니다.")
        )));
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "model", "gpt-5.4-mini",
                "usage", Map.of(
                        "input_tokens", 120,
                        "output_tokens", 80,
                        "total_tokens", 200,
                        "input_tokens_details", Map.of("cached_tokens", 20),
                        "output_tokens_details", Map.of("reasoning_tokens", 15)
                ),
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", quizJson))
                ))
        ));

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-5.4-mini"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.reasoning.effort").value("low"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.input[0].content[0].text", containsString("<topic>\nJava\n</topic>")))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        QuizResponse quiz = service.generateQuiz("Java", 1, List.of("JVM의 역할은 무엇인가요?"));

        assertThat(quiz.quizTitle()).isEqualTo("Java 기초 퀴즈");
        assertThat(quiz.questions()).hasSize(1);
        assertThat(quiz.questions().getFirst().correctAnswer()).isEqualTo("객체지향");
        assertThat(meterRegistry.find("auknowlog.ai.quiz.request.duration")
                .tags("model", "gpt-5.4-mini", "outcome", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.find("auknowlog.ai.quiz.tokens")
                .tags("model", "gpt-5.4-mini", "type", "total").summary().totalAmount()).isEqualTo(200);
        verify(aiGenerationLedgerService).recordQuizSuccess(
                org.mockito.ArgumentMatchers.eq("gpt-5.4-mini"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        server.verify();
    }

    @Test
    void rejectsMalformedQuizBeforeItReachesTheController() throws Exception {
        String quizJson = objectMapper.writeValueAsString(new QuizResponse("불완전한 퀴즈", List.of()));
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", quizJson))
                ))
        ));
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateQuiz("Java", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requested number of questions");
        server.verify();
    }

    @Test
    void includesSourceContextAndReferencesForGroundedQuizGeneration() throws Exception {
        String quizJson = objectMapper.writeValueAsString(new QuizResponse("JVM 퀴즈", List.of(
                new Question(
                        "JVM의 역할은 무엇인가요?",
                        List.of("바이트코드 실행", "이미지 편집", "문서 작성", "네트워크 차단"),
                        "바이트코드 실행",
                        "JVM은 Java 바이트코드를 실행합니다.",
                        List.of("source-7-chunk-1")
                )
        )));
        String responseBody = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "model", "gpt-5.4-mini",
                "usage", Map.of("input_tokens", 20, "output_tokens", 10, "total_tokens", 30),
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", quizJson))
                ))
        ));

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(jsonPath("$.input[0].content[0].text", containsString("[source-7-chunk-1]")))
                .andExpect(jsonPath("$.text.format.schema.properties.questions.items.properties.sourceReferences.type").value("array"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        QuizResponse quiz = service.generateQuiz(
                "JVM",
                1,
                List.of(),
                List.of(new SourceChunkContext("source-7-chunk-1", "JVM은 Java 바이트코드를 실행합니다."))
        );

        assertThat(quiz.questions().getFirst().sourceReferences()).containsExactly("source-7-chunk-1");
        server.verify();
    }
}
