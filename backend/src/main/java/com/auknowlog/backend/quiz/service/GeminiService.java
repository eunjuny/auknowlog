package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.quiz.dto.Content;
import com.auknowlog.backend.quiz.dto.GeminiRequest;
import com.auknowlog.backend.quiz.dto.GeminiResponse;
import com.auknowlog.backend.quiz.dto.Part;
import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeminiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${auknowlog.gemini.api.key}")
    private String apiKey;

    public GeminiService(WebClient.Builder webClientBuilder, @Value("${auknowlog.gemini.api.url}") String geminiApiUrl, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(geminiApiUrl).build();
        this.objectMapper = objectMapper;
    }

    public Mono<QuizResponse> generateQuiz(String topic, int numberOfQuestions) {
        String prompt = createQuizPrompt(topic, numberOfQuestions);
        GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));
        System.out.println("DEBUG: Gemini API Key being used: " + apiKey); // TEMPORARY DEBUG LOG

        if (apiKey == null || apiKey.isBlank() || "YOUR_API_KEY_HERE".equals(apiKey)) {
            System.out.println("DEBUG: Gemini API Key being used: " + apiKey); // TEMPORARY DEBUG LOG
            return Mono.error(new IllegalStateException("Gemini API key is not configured"));
        }

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", apiKey).build())
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new RuntimeException("Gemini API error: " + resp.statusCode() + " body=" + body)))
                .bodyToMono(GeminiResponse.class)
                .map(response -> parseToQuizResponse(response, numberOfQuestions, topic))
                .doOnError(ex -> log.error("Gemini call failed", ex));
    }

    private String createQuizPrompt(String topic, int numberOfQuestions) {
        String template = """
            You are a JSON API. Respond with ONLY JSON with no markdown or comments.
            Create a multiple-choice quiz about '%s'.
            The quiz must have a title and exactly %d questions.
            Each question must have exactly 4 options, one correctAnswer (must be one of the options), and a brief explanation.
            Respond strictly in this JSON schema: {"quizTitle":"...","questions":[{"questionText":"...","options":["...","...","...","..."],"correctAnswer":"...","explanation":"..."}]}
            """;
        return template.formatted(topic, numberOfQuestions);
    }

    private QuizResponse parseToQuizResponse(GeminiResponse geminiResponse, int numberOfQuestions, String topic) {
        // This is a simplified parser. In a real application, you'd use a robust JSON library
        // to parse the text content from the Gemini response into your QuizResponse DTO.
        // For now, we assume the response text is a well-formatted JSON string.
        try {
            String jsonResponse = geminiResponse.candidates().get(0).content().parts().get(0).text();
            String sanitized = sanitizeModelJson(jsonResponse);
            String extracted = extractFirstJsonObject(sanitized);
            return objectMapper.readValue(extracted, QuizResponse.class);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini JSON response", e);
        }
    }

    // Attempts to extract the first top-level JSON object from a text blob.
    private String extractFirstJsonObject(String text) {
        int start = -1;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return text; // fallback to original
    }

    private String sanitizeModelJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        // Remove Markdown fences
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
        }
        if (trimmed.startsWith("json")) {
            trimmed = trimmed.substring(4).trim();
        }
        return trimmed;
    }
}
