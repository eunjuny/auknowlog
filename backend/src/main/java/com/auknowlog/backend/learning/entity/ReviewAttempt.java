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
import java.util.UUID;

@Entity
@Table(name = "review_attempt")
public class ReviewAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_schedule_id", nullable = false)
    private ReviewSchedule reviewSchedule;

    @Column(nullable = false, unique = true)
    private UUID submissionId;

    @Column(nullable = false)
    private String selectedAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int intervalBefore;

    @Column(nullable = false)
    private int intervalAfter;

    @Column(nullable = false)
    private int repetitionAfter;

    @Column(nullable = false)
    private int lapseAfter;

    @Column(nullable = false, length = 32)
    private String statusAfter;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    private LocalDateTime nextReviewAt;

    protected ReviewAttempt() {
    }

    public ReviewAttempt(ReviewSchedule reviewSchedule, UUID submissionId, String selectedAnswer, boolean correct,
                         int intervalBefore, int intervalAfter, int repetitionAfter, int lapseAfter,
                         String statusAfter, LocalDateTime reviewedAt, LocalDateTime nextReviewAt) {
        this.reviewSchedule = reviewSchedule;
        this.submissionId = submissionId;
        this.selectedAnswer = selectedAnswer;
        this.correct = correct;
        this.intervalBefore = intervalBefore;
        this.intervalAfter = intervalAfter;
        this.repetitionAfter = repetitionAfter;
        this.lapseAfter = lapseAfter;
        this.statusAfter = statusAfter;
        this.reviewedAt = reviewedAt;
        this.nextReviewAt = nextReviewAt;
    }

    public ReviewSchedule getReviewSchedule() { return reviewSchedule; }
    public String getSelectedAnswer() { return selectedAnswer; }
    public boolean isCorrect() { return correct; }
    public int getIntervalAfter() { return intervalAfter; }
    public int getRepetitionAfter() { return repetitionAfter; }
    public int getLapseAfter() { return lapseAfter; }
    public String getStatusAfter() { return statusAfter; }
    public LocalDateTime getNextReviewAt() { return nextReviewAt; }
}
