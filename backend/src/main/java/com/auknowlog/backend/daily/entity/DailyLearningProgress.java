package com.auknowlog.backend.daily.entity;

import com.auknowlog.backend.auth.entity.AppUser;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_learning_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_learning_progress_owner", columnNames = {"daily_learning_id", "owner_id"}))
public class DailyLearningProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "daily_learning_id")
    private DailyLearning dailyLearning;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id")
    private AppUser owner;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DailyLearningStatus status;
    private LocalDateTime completedAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected DailyLearningProgress() {}
    public DailyLearningProgress(DailyLearning dailyLearning, AppUser owner) {
        this.dailyLearning = dailyLearning;
        this.owner = owner;
        this.status = DailyLearningStatus.READY;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
    public void complete() { this.status = DailyLearningStatus.COMPLETED; this.completedAt = LocalDateTime.now(); this.updatedAt = this.completedAt; }
    public DailyLearningStatus getStatus() { return status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
