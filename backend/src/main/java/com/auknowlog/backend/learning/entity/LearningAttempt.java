package com.auknowlog.backend.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "learning_attempt")
public class LearningAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private LearningQuiz quiz;

    @Column(nullable = false)
    private int totalQuestions;

    @Column(nullable = false)
    private int correctAnswers;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    protected LearningAttempt() {
    }

    public LearningAttempt(LearningQuiz quiz, int totalQuestions, int correctAnswers) {
        this.quiz = quiz;
        this.totalQuestions = totalQuestions;
        this.correctAnswers = correctAnswers;
        this.submittedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LearningQuiz getQuiz() {
        return quiz;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
