package com.auknowlog.backend.document.service;

import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DocumentService {

    private static final String SAVE_DIR = "./backend/src/main/resources/saved_quizzes/";

    public String saveQuizAsMarkdown(QuizResponse quizResponse) throws IOException {
        String markdownContent = convertQuizToMarkdown(quizResponse);
        String fileName = generateFileName(quizResponse.quizTitle());
        Path filePath = Paths.get(SAVE_DIR + fileName);

        Files.createDirectories(filePath.getParent()); // Ensure directory exists
        Files.writeString(filePath, markdownContent);

        return filePath.toString();
    }

    private String convertQuizToMarkdown(QuizResponse quizResponse) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(quizResponse.quizTitle()).append("\n\n");
        sb.append("## 생성일: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        List<Question> questions = quizResponse.questions();
        if (questions != null && !questions.isEmpty()) {
            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                sb.append("### ").append(i + 1).append(". ").append(q.questionText()).append("\n");
                if (q.options() != null) {
                    for (String option : q.options()) {
                        sb.append("- ").append(option).append("\n");
                    }
                }
                sb.append("\n**정답:** ").append(q.correctAnswer()).append("\n");
                sb.append("**해설:** ").append(q.explanation()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String generateFileName(String quizTitle) {
        String sanitizedTitle = quizTitle.replaceAll("[^a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\s]", "").replace(" ", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return sanitizedTitle + "_" + timestamp + ".md";
    }
}
