package com.auknowlog.backend.document.service;

import com.auknowlog.backend.learning.entity.LearningQuiz;
import com.auknowlog.backend.learning.repository.LearningQuizRepository;
import com.auknowlog.backend.auth.service.CurrentUserService;
import com.auknowlog.backend.roadmap.entity.LearningRoadmap;
import com.auknowlog.backend.roadmap.entity.LearningRoadmapStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesRoadmapQuizUnderMajorTopicAndSequencesRepeatedSubtopic() throws Exception {
        LearningQuizRepository repository = mock(LearningQuizRepository.class);
        LearningQuiz quiz = roadmapQuiz(41L, 7L, "Kubernetes 운영", "Kubernetes 기초", "Pod");
        CurrentUserService currentUser = currentUser();
        when(repository.findByIdAndOwnerId(41L, 1L)).thenReturn(Optional.of(quiz));
        DocumentService service = new DocumentService(repository, temporaryDirectory, currentUser);

        Path first = Path.of(service.saveQuizMarkdown(41L, "first note"));
        Path second = Path.of(service.saveQuizMarkdown(41L, "second note"));
        Path third = Path.of(service.saveQuizMarkdown(41L, "third note"));

        Path expectedDirectory = temporaryDirectory
                .resolve("roadmaps")
                .resolve("roadmap-7-Kubernetes_운영")
                .resolve("Kubernetes_기초");
        assertThat(first).isEqualTo(expectedDirectory.resolve("Pod.md").toAbsolutePath());
        assertThat(second).isEqualTo(expectedDirectory.resolve("Pod-2.md").toAbsolutePath());
        assertThat(third).isEqualTo(expectedDirectory.resolve("Pod-3.md").toAbsolutePath());
        assertThat(first).hasContent("first note");
        assertThat(second).hasContent("second note");
        assertThat(third).hasContent("third note");
    }

    @Test
    void usesMajorTopicAsFileNameWhenRoadmapStepHasNoSubtopic() throws Exception {
        LearningQuizRepository repository = mock(LearningQuizRepository.class);
        LearningQuiz quiz = roadmapQuiz(42L, 8L, "Java 로드맵", "JVM 운영", null);
        CurrentUserService currentUser = currentUser();
        when(repository.findByIdAndOwnerId(42L, 1L)).thenReturn(Optional.of(quiz));
        DocumentService service = new DocumentService(repository, temporaryDirectory, currentUser);

        Path stored = Path.of(service.saveQuizMarkdown(42L, "major topic note"));

        assertThat(stored).isEqualTo(temporaryDirectory
                .resolve("roadmaps")
                .resolve("roadmap-8-Java_로드맵")
                .resolve("JVM_운영")
                .resolve("JVM_운영.md")
                .toAbsolutePath());
    }

    private LearningQuiz roadmapQuiz(Long quizId, Long roadmapId, String roadmapTitle, String majorTopicTitle,
                                     String subtopicTitle) {
        LearningQuiz quiz = mock(LearningQuiz.class);
        LearningRoadmap roadmap = mock(LearningRoadmap.class);
        LearningRoadmapStep step = mock(LearningRoadmapStep.class);
        when(quiz.getId()).thenReturn(quizId);
        when(quiz.getRoadmap()).thenReturn(roadmap);
        when(quiz.getRoadmapStep()).thenReturn(step);
        when(roadmap.getId()).thenReturn(roadmapId);
        when(roadmap.getTitle()).thenReturn(roadmapTitle);
        when(step.getMajorTopicTitle()).thenReturn(majorTopicTitle);
        when(step.getSubtopicTitle()).thenReturn(subtopicTitle);
        return quiz;
    }

    private CurrentUserService currentUser() {
        CurrentUserService service = mock(CurrentUserService.class);
        when(service.currentUserId()).thenReturn(1L);
        return service;
    }
}
