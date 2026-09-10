package com.auknowlog.backend.feedback.repository;

import com.auknowlog.backend.feedback.entity.QuestionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.auknowlog.backend.feedback.entity.QuestionFeedbackType;

import java.util.List;
import java.util.Optional;

public interface QuestionFeedbackRepository extends JpaRepository<QuestionFeedback, Long> {

    Optional<QuestionFeedback> findByQuestionId(Long questionId);

    long countByStatus(String status);

    @Query("""
            select question.questionText
            from QuestionFeedback feedback
            join feedback.question question
            join question.quiz quiz
            where feedback.feedbackType = :feedbackType
              and feedback.status = 'OPEN'
              and lower(quiz.topic) = lower(:topic)
            order by feedback.updatedAt desc
            """)
    List<String> findQuestionTextsByTypeAndTopic(
            @Param("feedbackType") QuestionFeedbackType feedbackType,
            @Param("topic") String topic,
            Pageable pageable
    );
}
