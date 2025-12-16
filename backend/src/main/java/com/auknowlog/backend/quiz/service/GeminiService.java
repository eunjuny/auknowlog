package com.auknowlog.backend.quiz.service;

import com.auknowlog.backend.quiz.dto.Content;
import com.auknowlog.backend.quiz.dto.GeminiRequest;
import com.auknowlog.backend.quiz.dto.GeminiResponse;
import com.auknowlog.backend.quiz.dto.Part;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeminiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${auknowlog.gemini.api.key}")
    private String apiKey;

    @Value("${auknowlog.gemini.api.url}")
    private String apiUrlBase;

    @Value("${auknowlog.gemini.model}")
    private String modelName;

    public GeminiService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public QuizResponse generateQuiz(String topic, int numberOfQuestions) {
        return generateQuiz(topic, numberOfQuestions, List.of());
    }

    public QuizResponse generateQuiz(String topic, int numberOfQuestions, List<String> existingQuestions) {
        String prompt = createQuizPrompt(topic, numberOfQuestions, existingQuestions);
        GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));

        if (apiKey == null || apiKey.isBlank() || "YOUR_API_KEY_HERE".equals(apiKey)) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String primaryUrl = String.format("%s/%s:generateContent?key=%s", apiUrlBase, modelName, apiKey);
        String fallbackUrl = buildFallbackUrl();

        try {
            GeminiResponse response = callGeminiWithRetry(primaryUrl, request);
            return parseToQuizResponse(response, numberOfQuestions, topic);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            boolean is404 = msg.contains("404") || msg.contains("NOT_FOUND") || msg.contains("was not found");
            if (is404 && fallbackUrl != null && !fallbackUrl.equals(primaryUrl)) {
                log.warn("Gemini primary call 404, falling back to {}", fallbackUrl);
                GeminiResponse response = callGeminiWithRetry(fallbackUrl, request);
                return parseToQuizResponse(response, numberOfQuestions, topic);
            }
            log.error("Gemini call failed", ex);
            throw ex;
        }
    }

    private GeminiResponse callGemini(String url, GeminiRequest request) {
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeminiResponse.class);
    }

    /**
     * Gemini API가 503(과부하), 429(레이트리밋) 등 일시 장애를 반환할 수 있어
     * 지수 백오프 + Retry-After 헤더를 존중하여 재시도합니다.
     * (Virtual Threads 환경이므로 Thread.sleep이 부담이 적습니다.)
     */
    private GeminiResponse callGeminiWithRetry(String url, GeminiRequest request) {
        final int maxAttempts = 5;
        final long baseDelayMs = 500;
        final long maxDelayMs = 10_000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGemini(url, request);
            } catch (HttpStatusCodeException e) {
                HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
                boolean retryable = status == HttpStatus.SERVICE_UNAVAILABLE
                        || status == HttpStatus.TOO_MANY_REQUESTS
                        || status == HttpStatus.BAD_GATEWAY
                        || status == HttpStatus.GATEWAY_TIMEOUT;

                if (retryable && attempt < maxAttempts) {
                    long delay = computeRetryDelayMs(e.getResponseHeaders(), attempt, baseDelayMs, maxDelayMs);
                    log.warn("Gemini 일시 오류({})로 재시도합니다. attempt {}/{} (sleep {}ms)",
                            e.getStatusCode(), attempt, maxAttempts, delay);
                    sleepQuietly(delay);
                    continue;
                }

                // 마지막 시도에서도 503이면, 사용자에게 명확히 보이도록 별도 예외로 변환
                if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                    throw new com.auknowlog.backend.common.exception.GeminiOverloadedException(
                            "Gemini 모델이 혼잡합니다. 잠시 후 다시 시도해주세요.", e);
                }
                throw e;
            }
        }

        throw new com.auknowlog.backend.common.exception.GeminiOverloadedException(
                "Gemini 모델이 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    private long computeRetryDelayMs(HttpHeaders headers, int attempt, long baseDelayMs, long maxDelayMs) {
        // Retry-After: seconds (간단 케이스만 처리)
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    long sec = Long.parseLong(retryAfter.trim());
                    return Math.min(maxDelayMs, Math.max(0, Duration.ofSeconds(sec).toMillis()));
                } catch (Exception ignored) { }
            }
        }

        // 지수 백오프 + 지터
        long exp = baseDelayMs * (1L << Math.max(0, attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, 250);
        return Math.min(maxDelayMs, exp + jitter);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
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
            String fallbackModel = modelName != null && modelName.endsWith("-latest") ? modelName : (modelName == null ? "gemini-1.5-flash-latest" : modelName + "-latest");
            return String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", fallbackModel, apiKey);
        } catch (Exception e) {
            return null;
        }
    }

    private String createQuizPrompt(String topic, int numberOfQuestions) {
        return createQuizPrompt(topic, numberOfQuestions, List.of());
    }

    private String createQuizPrompt(String topic, int numberOfQuestions, List<String> existingQuestions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a JSON API. Respond with ONLY JSON with no markdown or comments.\n");
        prompt.append("Create a multiple-choice quiz about '").append(topic).append("'.\n");
        prompt.append("The quiz must have a title and exactly ").append(numberOfQuestions).append(" questions.\n");
        prompt.append("Each question must have exactly 4 options, one correctAnswer (must be one of the options), and a brief explanation.\n");
        
        // 기존 문제 목록이 있으면 포함 (토큰 최소화)
        if (!existingQuestions.isEmpty()) {
            prompt.append("\nIMPORTANT: Do NOT create questions similar to these existing ones:\n");
            for (int i = 0; i < existingQuestions.size(); i++) {
                prompt.append("- ").append(existingQuestions.get(i)).append("\n");
            }
            prompt.append("Create completely different and new questions.\n");
        }
        
        prompt.append("Respond strictly in this JSON schema: {\"quizTitle\":\"...\",\"questions\":[{\"questionText\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"correctAnswer\":\"...\",\"explanation\":\"...\"}]}");
        
        return prompt.toString();
    }

    private QuizResponse parseToQuizResponse(GeminiResponse geminiResponse, int numberOfQuestions, String topic) {
        try {
            String jsonResponse = geminiResponse.candidates().get(0).content().parts().get(0).text();
            String sanitized = sanitizeModelJson(jsonResponse);
            String extracted = extractFirstJsonObject(sanitized);
            return objectMapper.readValue(extracted, QuizResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini JSON response", e);
        }
    }

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
        return text;
    }

    private String sanitizeModelJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
        }
        if (trimmed.startsWith("json")) {
            trimmed = trimmed.substring(4).trim();
        }
        return trimmed;
    }

    public String renderQuizMarkdownLocally(Map<String, Object> payload) {
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
        int unansweredToShow = statsMap == null ? computedUnanswered : 0;
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

        return sb.toString();
    }

    private String deriveUserSelectedAnswer(Map<String, Object> q, List<String> options, Integer userIndex) {
        if (userIndex == null || options == null) return null;
        if (userIndex < 0 || userIndex >= options.size()) return null;
        return options.get(userIndex);
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
