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
@Table(name = "review_schedule")
public class ReviewSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private LearningQuestion question;

    @Column(nullable = false)
    private LocalDateTime nextReviewAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ReviewSchedule() {
    }

    public ReviewSchedule(LearningQuestion question, LocalDateTime nextReviewAt) {
        this.question = question;
        this.nextReviewAt = nextReviewAt;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}
