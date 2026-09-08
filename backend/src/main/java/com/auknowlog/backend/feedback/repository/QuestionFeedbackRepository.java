package com.auknowlog.backend.feedback.repository;

import com.auknowlog.backend.feedback.entity.QuestionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionFeedbackRepository extends JpaRepository<QuestionFeedback, Long> {

    Optional<QuestionFeedback> findByQuestionId(Long questionId);

    long countByStatus(String status);
}
