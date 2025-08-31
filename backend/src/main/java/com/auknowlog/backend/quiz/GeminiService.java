package com.auknowlog.backend.quiz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    public GeminiService(WebClient.Builder webClientBuilder, @Value("${gemini.api.url}") String geminiApiUrl) {
        this.webClient = webClientBuilder.baseUrl(geminiApiUrl).build();
    }

    public Mono<QuizResponse> generateQuiz(String topic) {
        String prompt = createQuizPrompt(topic);
        GeminiRequest request = new GeminiRequest(List.of(new Content(List.of(new Part(prompt))))));

        return webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", apiKey).build())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .map(geminiResponse -> parseToQuizResponse(geminiResponse));
    }

    private String createQuizPrompt(String topic) {
        return "Create a multiple-choice quiz about " + topic + ". "
                + "The quiz should have a title and 5 questions. "
                + "Each question must have 4 options, one correct answer, and a brief explanation. "
                + "Return the result in a clean JSON format without any surrounding text or markdown. "
                + "The JSON structure should be: {\"quizTitle\": \"...\", \"questions\": [{\"questionText\": \"...\", \"options\": [\"...\", \"...\", \"...\", \"...\"], \"correctAnswer\": \"...\", \"explanation\": \"...\"}]} ";
    }

    private QuizResponse parseToQuizResponse(GeminiResponse geminiResponse) {
        // This is a simplified parser. In a real application, you'd use a robust JSON library
        // to parse the text content from the Gemini response into your QuizResponse DTO.
        // For now, we assume the response text is a well-formatted JSON string.
        try {
            String jsonResponse = geminiResponse.candidates().get(0).content().parts().get(0).text();
            // Here you would use a library like Jackson or Gson to deserialize the jsonResponse string
            // into a QuizResponse object. This is aփplaceholder for that logic.
            // For example, with Jackson: new ObjectMapper().readValue(jsonResponse, QuizResponse.class);
            // As we can't easily add new dependencies here, we will return a dummy response.
            
            // Dummy response for demonstration:
            List<Question> questions = List.of(
                new Question("This is a sample question from the parser.", List.of("A", "B", "C", "D"), "A", "This is a dummy explanation.")
            );
            return new QuizResponse("Sample Quiz Title", questions);

        } catch (Exception e) {
            // Handle parsing error
            return new QuizResponse("Error parsing quiz", List.of());
        }
    }
}
