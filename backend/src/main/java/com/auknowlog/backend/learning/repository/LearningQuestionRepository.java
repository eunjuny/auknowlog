package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningQuestionRepository extends JpaRepository<LearningQuestion, Long> {

    List<LearningQuestion> findByQuizIdOrderByQuestionOrderAsc(Long quizId);

    Optional<LearningQuestion> findByQuizIdAndQuestionOrder(Long quizId, int questionOrder);
}
