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

    @Value("${auknowlog.gemini.api.url}")
    private String apiUrlBase;

    @Value("${auknowlog.gemini.model}")
    private String modelName;

    public GeminiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
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

        String primaryUrl = String.format("%s/%s:generateContent?key=%s", apiUrlBase, modelName, apiKey);
        String fallbackUrl = buildFallbackUrl();

        return callGemini(primaryUrl, request)
                .onErrorResume(ex -> {
                    String msg = ex.getMessage() == null ? "" : ex.getMessage();
                    boolean is404 = msg.contains("404") || msg.contains("NOT_FOUND") || msg.contains("was not found");
                    if (is404 && fallbackUrl != null && !fallbackUrl.equals(primaryUrl)) {
                        log.warn("Gemini primary call 404, falling back to {}", fallbackUrl);
                        return callGemini(fallbackUrl, request);
                    }
                    return Mono.error(ex);
                })
                .map(response -> parseToQuizResponse(response, numberOfQuestions, topic))
                .doOnError(ex -> log.error("Gemini call failed", ex));
    }

    private Mono<GeminiResponse> callGemini(String url, GeminiRequest request) {
        return webClient.post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new RuntimeException("Gemini API error: " + resp.statusCode() + " body=" + body)))
                .bodyToMono(GeminiResponse.class);
    }

    private String buildFallbackUrl() {
        try {
            boolean isV1 = apiUrlBase != null && apiUrlBase.contains("/v1/");
            boolean isV1beta = apiUrlBase != null && apiUrlBase.contains("/v1beta/");
            if (isV1) {
                String fallbackModel = modelName != null && modelName.endsWith("-latest") ? modelName : modelName + "-latest";
                return String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", fallbackModel, apiKey);
            } else if (isV1beta) {
                String baseModel = modelName != null ? modelName.replace("-latest", "") : "gemini-1.5-flash";
                return String.format("https://generativelanguage.googleapis.com/v1/models/%s:generateContent?key=%s", baseModel, apiKey);
            }
            // 기본적으로 v1beta latest로 폴백
            String fallbackModel = modelName != null && modelName.endsWith("-latest") ? modelName : (modelName == null ? "gemini-1.5-flash-latest" : modelName + "-latest");
            return String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", fallbackModel, apiKey);
        } catch (Exception e) {
            return null;
        }
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

    // LLM 호출 없이 로컬 렌더링만 사용합니다. (renderQuizMarkdownLocally 참고)


    public Mono<String> renderQuizMarkdownLocally(Map<String, Object> payload) {
        String title = asString(payload.get("quizTitle"));
        if (title == null || title.isBlank()) {
            title = "퀴즈 결과";
        }

        List<?> questionListRaw = asList(payload.get("questions"));
        int total = questionListRaw != null ? questionListRaw.size() : 0;

        Integer statsTotal = null, statsCorrect = null, statsWrong = null;
        Map<String, Object> statsMap = asMap(payload.get("stats"));
        if (statsMap != null) {
            statsTotal = asInteger(statsMap.get("total"));
            statsCorrect = asInteger(statsMap.get("correct"));
            statsWrong = asInteger(statsMap.get("wrong"));
        }

        int computedCorrect = 0;
        int computedWrong = 0;
        int computedUnanswered = 0;

        if (questionListRaw != null) {
            for (int i = 0; i < questionListRaw.size(); i++) {
                Map<String, Object> q = asMap(questionListRaw.get(i));
                if (q == null) continue;

                Boolean isCorrect = asBoolean(q.get("isCorrect"));
                Integer userIndex = asInteger(q.get("userSelectedIndex"));
                String correctAnswer = asString(q.get("correctAnswer"));
                List<String> options = asStringList(q.get("options"));

                if (userIndex == null) {
                    computedUnanswered++;
                } else {
                    if (isCorrect != null) {
                        if (isCorrect) computedCorrect++; else computedWrong++;
                    } else {
                        String userAnswer = deriveUserSelectedAnswer(q, options, userIndex);
                        if (userAnswer != null && correctAnswer != null) {
                            if (userAnswer.trim().equals(correctAnswer.trim())) computedCorrect++; else computedWrong++;
                        }
                    }
                }
            }
        }

        int headerTotal = statsTotal != null ? statsTotal : total;
        int headerCorrect = statsCorrect != null ? statsCorrect : computedCorrect;
        int headerWrong = statsWrong != null ? statsWrong : computedWrong;

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append('\n');
        sb.append("총 ").append(headerTotal).append("문항 · 정답 ").append(headerCorrect).append(" · 오답 ").append(headerWrong);
        int unansweredToShow;
        if (statsMap == null) {
            unansweredToShow = computedUnanswered;
        } else {
            unansweredToShow = 0; // stats가 있으면 별도 표기 지침이 없으므로 생략
        }
        if (unansweredToShow > 0) {
            sb.append(" · 미응답 ").append(unansweredToShow);
        }

        if (questionListRaw != null && !questionListRaw.isEmpty()) {
            for (int i = 0; i < questionListRaw.size(); i++) {
                Map<String, Object> q = asMap(questionListRaw.get(i));
                if (q == null) continue;

                String questionText = asString(q.get("questionText"));
                List<String> options = asStringList(q.get("options"));
                String correctAnswer = asString(q.get("correctAnswer"));
                String explanation = asString(q.get("explanation"));
                Integer userIndex = asInteger(q.get("userSelectedIndex"));
                String userAnswer = asString(q.get("userSelectedAnswer"));
                if ((userAnswer == null || userAnswer.isBlank()) && userIndex != null) {
                    userAnswer = deriveUserSelectedAnswer(q, options, userIndex);
                }
                Boolean isCorrect = asBoolean(q.get("isCorrect"));
                if (isCorrect == null && userAnswer != null && correctAnswer != null) {
                    isCorrect = userAnswer.trim().equals(correctAnswer.trim());
                }

                sb.append('\n');
                sb.append("\n## Q").append(i + 1).append(". ").append(questionText != null ? questionText : "").append('\n');

                if (options != null) {
                    for (int idx = 0; idx < options.size(); idx++) {
                        char label = (char) ('A' + idx);
                        sb.append("- ").append(label).append(". ").append(options.get(idx) != null ? options.get(idx) : "").append('\n');
                    }
                }

                // 선택/정오 표시 한 줄
                if (userIndex == null) {
                    sb.append("❗ 미응답");
                    if (correctAnswer != null && !correctAnswer.isBlank()) {
                        sb.append("  |  정답: ").append(correctAnswer);
                    }
                } else if (Boolean.TRUE.equals(isCorrect)) {
                    sb.append("✅ 내 답: ").append(userAnswer != null ? userAnswer : "");
                } else {
                    sb.append("❌ 내 답: ").append(userAnswer != null ? userAnswer : "");
                    if (correctAnswer != null && !correctAnswer.isBlank()) {
                        sb.append("  |  정답: ").append(correctAnswer);
                    }
                }

                if (explanation != null && !explanation.isBlank()) {
                    sb.append('\n').append("설명: ").append(explanation);
                }
            }
        }

        return Mono.just(sb.toString());
    }

    private String deriveUserSelectedAnswer(Map<String, Object> q, List<String> options, Integer userIndex) {
        if (userIndex == null || options == null) return null;
        if (userIndex < 0 || userIndex >= options.size()) return null;
        String candidate = options.get(userIndex);
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private List<?> asList(Object o) {
        if (o instanceof List<?> list) return list;
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Integer asInteger(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return o != null ? Integer.parseInt(String.valueOf(o)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean asBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) {
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        return null;
    }

    private List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            List<String> out = new java.util.ArrayList<>();
            for (Object it : list) out.add(it == null ? null : String.valueOf(it));
            return out;
        }
        return null;
    }
}
