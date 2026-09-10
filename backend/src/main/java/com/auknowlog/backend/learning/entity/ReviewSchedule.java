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
import jakarta.persistence.Version;

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
    private int intervalDays;

    @Column(nullable = false)
    private int repetitionCount;

    @Column(nullable = false)
    private int lapseCount;

    private LocalDateTime lastReviewedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ReviewSchedule() {
    }

    public ReviewSchedule(LearningQuestion question, LocalDateTime nextReviewAt) {
        this.question = question;
        this.nextReviewAt = nextReviewAt;
        this.status = "PENDING";
        this.intervalDays = 1;
        this.repetitionCount = 0;
        this.lapseCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public ReviewTransition recordAnswer(boolean correct, LocalDateTime reviewedAt) {
        int intervalBefore = intervalDays;
        lastReviewedAt = reviewedAt;
        if (correct) {
            repetitionCount++;
            intervalDays = switch (repetitionCount) {
                case 1 -> 3;
                case 2 -> 7;
                case 3 -> 14;
                default -> 30;
            };
            if (repetitionCount >= 5) {
                status = "COMPLETED";
                nextReviewAt = reviewedAt.plusDays(intervalDays);
            } else {
                status = "PENDING";
                nextReviewAt = reviewedAt.plusDays(intervalDays);
            }
        } else {
            repetitionCount = 0;
            lapseCount++;
            intervalDays = 1;
            status = "PENDING";
            nextReviewAt = reviewedAt.plusDays(1);
        }
        updatedAt = reviewedAt;
        return new ReviewTransition(intervalBefore, intervalDays,
                "COMPLETED".equals(status) ? null : nextReviewAt);
    }

    public Long getId() { return id; }
    public LearningQuestion getQuestion() { return question; }
    public LocalDateTime getNextReviewAt() { return nextReviewAt; }
    public String getStatus() { return status; }
    public int getIntervalDays() { return intervalDays; }
    public int getRepetitionCount() { return repetitionCount; }
    public int getLapseCount() { return lapseCount; }
    public LocalDateTime getLastReviewedAt() { return lastReviewedAt; }

    public record ReviewTransition(int intervalBefore, int intervalAfter, LocalDateTime nextReviewAt) {
    }
}
