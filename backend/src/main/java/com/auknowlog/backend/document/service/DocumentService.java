package com.auknowlog.backend.document.service;

import com.auknowlog.backend.quiz.dto.Question;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.learning.entity.LearningQuiz;
import com.auknowlog.backend.daily.entity.DailyLearning;
import com.auknowlog.backend.daily.entity.DailyLearningTrack;
import com.auknowlog.backend.learning.repository.LearningQuizRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final Path SAVE_DIR = Paths.get("./src/main/resources/saved_quizzes/");
    private final LearningQuizRepository learningQuizRepository;
    private final Path saveDirectory;

    @Autowired
    public DocumentService(LearningQuizRepository learningQuizRepository) {
        this(learningQuizRepository, SAVE_DIR);
    }

    /** 테스트에서는 임시 디렉터리를 주입해 실제 학습 노트 디렉터리를 건드리지 않는다. */
    DocumentService(LearningQuizRepository learningQuizRepository, Path saveDirectory) {
        this.learningQuizRepository = learningQuizRepository;
        this.saveDirectory = saveDirectory;
    }

    public String saveQuizAsMarkdown(QuizResponse quizResponse) throws IOException {
        String markdownContent = convertQuizToMarkdown(quizResponse);
        String fileName = generateFileName(quizResponse.quizTitle());
        Path filePath = saveDirectory.resolve(fileName);

        Files.createDirectories(filePath.getParent()); // Ensure directory exists
        Files.writeString(filePath, markdownContent);

        return filePath.toAbsolutePath().toString();
    }

    public String saveMarkdownContent(String quizTitle, String markdownContent) throws IOException {
        String safeTitle = (quizTitle == null || quizTitle.isBlank()) ? "퀴즈_결과" : quizTitle;
        String fileName = generateFileName(safeTitle);
        Path filePath = saveDirectory.resolve(fileName);

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, markdownContent);
        return filePath.toAbsolutePath().toString();
    }

    /**
     * 로드맵 퀴즈는 브라우저가 보낸 제목이나 경로를 믿지 않고, 저장된 quizId의 관계를 기준으로 정리한다.
     * notes 원격에는 roadmaps/roadmap-{id}-{title}/{대주제}/{소주제}.md 아래에 누적된다.
     * 같은 소주제를 다시 저장하면 두 번째부터 -2, -3 suffix를 붙여 기존 노트를 덮어쓰지 않는다.
     */
    @Transactional(readOnly = true)
    public String saveQuizMarkdown(Long quizId, String markdownContent) throws IOException {
        if (quizId == null || quizId < 1) {
            throw new IllegalArgumentException("저장할 퀴즈 식별자가 필요합니다.");
        }
        LearningQuiz quiz = learningQuizRepository.findById(quizId)
                .orElseThrow(() -> new java.util.NoSuchElementException("저장할 퀴즈를 찾을 수 없습니다."));

        Path directory = saveDirectory;
        String fileBaseName = "quiz-" + quiz.getId();
        if (quiz.getDailyLearning() != null) {
            DailyLearning daily = quiz.getDailyLearning();
            Path dailyDirectory = saveDirectory.resolve("daily-tech")
                    .resolve(daily.getLearningDate() + "-" + directoryName(daily.getArticleTitle()));
            writeDailyOverview(dailyDirectory, daily);
            directory = dailyDirectory.resolve(quiz.getDailyLearningTrack() == DailyLearningTrack.ADVANCED
                    ? "advanced" : "review");
            fileBaseName = directoryName(quiz.getTopic());
        } else if (quiz.getRoadmap() != null) {
            directory = directory
                    .resolve("roadmaps")
                    .resolve("roadmap-" + quiz.getRoadmap().getId() + "-" + directoryName(quiz.getRoadmap().getTitle()));
            if (quiz.getRoadmapStep() != null) {
                String majorTopic = directoryName(quiz.getRoadmapStep().getMajorTopicTitle());
                String subtopic = quiz.getRoadmapStep().getSubtopicTitle();
                directory = directory.resolve(majorTopic);
                // 소주제가 없는 대주제 단독 단계는 대주제 이름을 파일명으로 사용한다.
                fileBaseName = directoryName(subtopic == null || subtopic.isBlank()
                        ? quiz.getRoadmapStep().getMajorTopicTitle()
                        : subtopic);
            }
        }
        Files.createDirectories(directory);
        Path filePath = writeSequentialMarkdown(directory, fileBaseName, markdownContent);
        return filePath.toAbsolutePath().toString();
    }

    /** 데일리 학습은 기사·해설을 learning.md에, 복습/심화 문항을 하위 폴더에 분리한다. */
    private void writeDailyOverview(Path dailyDirectory, DailyLearning daily) throws IOException {
        Files.createDirectories(dailyDirectory);
        Path overview = dailyDirectory.resolve("learning.md");
        if (Files.exists(overview)) return;
        String content = "# " + daily.getArticleTitle() + "\n\n"
                + "- 학습 일자: " + daily.getLearningDate() + "\n"
                + "- 원문: " + daily.getArticleUrl() + "\n"
                + "- 복습 주제: " + daily.getReviewTopic() + "\n\n"
                + "## 기사 요약\n\n" + daily.getArticleSummary() + "\n\n"
                + "## 보충 해설\n\n" + daily.getSupplement() + "\n\n"
                + "## 핵심 개념 (구조화 데이터)\n\n```json\n" + daily.getConcepts() + "\n```\n";
        Files.writeString(overview, content, StandardOpenOption.CREATE_NEW);
    }

    /** CREATE_NEW로 생성해 동시에 저장해도 기존 학습 노트를 덮어쓰지 않는다. */
    private Path writeSequentialMarkdown(Path directory, String fileBaseName, String markdownContent) throws IOException {
        String normalizedBaseName = directoryName(fileBaseName);
        for (int occurrence = 1; ; occurrence++) {
            String suffix = occurrence == 1 ? "" : "-" + occurrence;
            Path candidate = directory.resolve(normalizedBaseName + suffix + ".md");
            try {
                Files.writeString(candidate, markdownContent, StandardOpenOption.CREATE_NEW);
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
                // 같은 소주제의 이전 저장본이 있으면 다음 번호로 시도한다.
            }
        }
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
