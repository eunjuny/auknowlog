package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LearningAttemptRepository extends JpaRepository<LearningAttempt, Long> {

    Page<LearningAttempt> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    @Query("select attempt from LearningAttempt attempt join fetch attempt.quiz")
    List<LearningAttempt> findAllWithQuiz();

    Optional<LearningAttempt> findByQuizId(Long quizId);

    @Query("""
            select attempt from LearningAttempt attempt
            join fetch attempt.quiz quiz
            where quiz.roadmap.id = :roadmapId
              and attempt.submittedAt >= :start
              and attempt.submittedAt < :end
            """)
    List<LearningAttempt> findRoadmapAttemptsBetween(@Param("roadmapId") Long roadmapId,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    @Query("""
            select quiz.roadmapStep.id, sum(attempt.totalQuestions)
            from LearningAttempt attempt
            join attempt.quiz quiz
            where quiz.roadmap.id = :roadmapId
              and quiz.roadmapStep is not null
            group by quiz.roadmapStep.id
            """)
    List<Object[]> findRoadmapStepCompletedQuestions(@Param("roadmapId") Long roadmapId);
}
