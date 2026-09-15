package com.auknowlog.backend.document.service;

import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.learning.entity.LearningQuiz;
import com.auknowlog.backend.learning.repository.LearningQuizRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final String SAVE_DIR = "./src/main/resources/saved_quizzes/";
    private final LearningQuizRepository learningQuizRepository;

    public DocumentService(LearningQuizRepository learningQuizRepository) {
        this.learningQuizRepository = learningQuizRepository;
    }

    public String saveQuizAsMarkdown(QuizResponse quizResponse) throws IOException {
        String markdownContent = convertQuizToMarkdown(quizResponse);
        String fileName = generateFileName(quizResponse.quizTitle());
        Path filePath = Paths.get(SAVE_DIR + fileName);

        Files.createDirectories(filePath.getParent()); // Ensure directory exists
        Files.writeString(filePath, markdownContent);

        return filePath.toAbsolutePath().toString();
    }

    public String saveMarkdownContent(String quizTitle, String markdownContent) throws IOException {
        String safeTitle = (quizTitle == null || quizTitle.isBlank()) ? "퀴즈_결과" : quizTitle;
        String fileName = generateFileName(safeTitle);
        Path filePath = Paths.get(SAVE_DIR + fileName);

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, markdownContent);
        return filePath.toAbsolutePath().toString();
    }

    /**
     * 로드맵 퀴즈는 브라우저가 보낸 제목이나 경로를 믿지 않고, 저장된 quizId의 관계를 기준으로 정리한다.
     * notes 원격에는 roadmaps/roadmap-{id}-{title}/step-{순서}-{title}/ 아래에 누적된다.
     */
    @Transactional(readOnly = true)
    public String saveQuizMarkdown(Long quizId, String markdownContent) throws IOException {
        if (quizId == null || quizId < 1) {
            throw new IllegalArgumentException("저장할 퀴즈 식별자가 필요합니다.");
        }
        LearningQuiz quiz = learningQuizRepository.findById(quizId)
                .orElseThrow(() -> new java.util.NoSuchElementException("저장할 퀴즈를 찾을 수 없습니다."));

        Path directory = Paths.get(SAVE_DIR);
        if (quiz.getRoadmap() != null) {
            directory = directory
                    .resolve("roadmaps")
                    .resolve("roadmap-" + quiz.getRoadmap().getId() + "-" + directoryName(quiz.getRoadmap().getTitle()));
            if (quiz.getRoadmapStep() != null) {
                directory = directory.resolve("step-%02d-%s".formatted(
                        quiz.getRoadmapStep().getStepOrder(), directoryName(quiz.getRoadmapStep().getTitle())));
            }
        }
        String fileName = "quiz-%d-%s.md".formatted(quiz.getId(), timestamp());
        Path filePath = directory.resolve(fileName);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, markdownContent);
        return filePath.toAbsolutePath().toString();
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
        return directoryName(quizTitle) + "_" + timestamp() + ".md";
    }

    private String directoryName(String value) {
        String sanitized = (value == null ? "" : value)
                .replaceAll("[^a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\\s_-]", "")
                .trim().replaceAll("\\s+", "_");
        return sanitized.isBlank() ? "untitled" : sanitized;
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
