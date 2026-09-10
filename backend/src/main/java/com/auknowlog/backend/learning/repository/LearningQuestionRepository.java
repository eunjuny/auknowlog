package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningQuestionRepository extends JpaRepository<LearningQuestion, Long> {

    List<LearningQuestion> findByQuizIdOrderByQuestionOrderAsc(Long quizId);

    Optional<LearningQuestion> findByQuizIdAndQuestionOrder(Long quizId, int questionOrder);

    @Query(value = "SELECT * FROM learning_question WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<LearningQuestion> findByIdForUpdate(@Param("id") Long id);
}
