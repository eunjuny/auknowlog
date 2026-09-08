package com.auknowlog.backend.feedback.entity;

import com.auknowlog.backend.learning.entity.LearningQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_feedback", uniqueConstraints = @UniqueConstraint(
        name = "uk_question_feedback_question", columnNames = "question_id"))
public class QuestionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private LearningQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 64)
    private QuestionFeedbackType feedbackType;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected QuestionFeedback() {
    }

    public QuestionFeedback(LearningQuestion question, QuestionFeedbackType feedbackType, String comment) {
        this.question = question;
        this.feedbackType = feedbackType;
        this.comment = comment;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(QuestionFeedbackType feedbackType, String comment) {
        this.feedbackType = feedbackType;
        this.comment = comment;
        this.status = "OPEN";
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LearningQuestion getQuestion() {
        return question;
    }

    public QuestionFeedbackType getFeedbackType() {
        return feedbackType;
    }

    public String getComment() {
        return comment;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
