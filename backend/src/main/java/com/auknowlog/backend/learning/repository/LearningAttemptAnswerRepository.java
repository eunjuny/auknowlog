package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningAttemptAnswerRepository extends JpaRepository<LearningAttemptAnswer, Long> {

    List<LearningAttemptAnswer> findByAttemptIdOrderByQuestionQuestionOrderAsc(Long attemptId);

    Optional<LearningAttemptAnswer> findByQuestionId(Long questionId);

    @Query("""
            select answer.question.learningObjective.id,
                   count(answer.id),
                   sum(case when answer.correct = true then 1 else 0 end)
            from LearningAttemptAnswer answer
            where answer.question.quiz.roadmap.id = :roadmapId
              and answer.question.learningObjective is not null
            group by answer.question.learningObjective.id
            """)
    List<Object[]> findRoadmapObjectiveProgress(@Param("roadmapId") Long roadmapId);
}
