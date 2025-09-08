package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.quiz.dto.Content;
import com.auknowlog.backend.quiz.dto.GeminiRequest;
import com.auknowlog.backend.quiz.dto.GeminiResponse;
import com.auknowlog.backend.quiz.dto.Part;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
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

    public Mono<String> renderQuizMarkdown(Map<String, Object> payload) {
        String systemPrompt = """
            너는 퀴즈 결과 JSON을 마크다운으로 렌더링하는 생성기다. 출력은 오직 마크다운 본문이며 코드펜스(````), 언어 태그, HTML, 불필요한 설명·메타텍스트를 절대 포함하지 않는다. 한글로 작성한다.

            규칙:
            - 첫 줄: "# {quizTitle}" (없으면 "퀴즈 결과")
            - 둘째 줄: "총 {N}문항 · 정답 {M} · 오답 {K}" 
              - stats(total, correct, wrong)가 없으면 questions[].isCorrect를 이용해 계산하되, 미응답은 wrong에 포함하지 말고 뒤에 " · 미응답 {U}"를 덧붙여 표기한다.
            - 각 문항 블록:
              - "## Q{번호}. {questionText}"
              - 보기 목록(각 줄): "- A. {옵션텍스트}" (인덱스 0→A, 1→B, 2→C, 3→D)
              - 선택/정오 표시(보기 목록 아래 한 줄):
                - isCorrect=true → "✅ 내 답: {userSelectedAnswer}"
                - isCorrect=false → "❌ 내 답: {userSelectedAnswer}  |  정답: {correctAnswer}"
                - userSelectedIndex가 null/undefined → "❗ 미응답  |  정답: {correctAnswer}"
              - explanation이 존재하면 마지막 줄에 "설명: {explanation}"
            - 입력에 없는 값은 추측하지 않는다. 개행은 과도하지 않게 유지한다.
            """;

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Failed to serialize payload to JSON", e));
        }

        String userPrompt = """
            아래 JSON(payload)을 위 규칙에 맞춰 마크다운으로 변환해줘.

            %s
            """.formatted(payloadJson);

        String combinedPrompt = systemPrompt + "\n\n" + userPrompt;

        GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(combinedPrompt)))));

        if (apiKey == null || apiKey.isBlank() || "YOUR_API_KEY_HERE".equals(apiKey)) {
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
                .map(response -> {
                    String text = response.candidates().get(0).content().parts().get(0).text();
                    return sanitizeModelText(text);
                })
                .doOnError(ex -> log.error("Gemini markdown render failed", ex));
    }

    private String sanitizeModelText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length() >= 6) {
            trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
            if (trimmed.startsWith("text")) {
                trimmed = trimmed.substring(4).trim();
            }
        }
        return trimmed;
    }
}
