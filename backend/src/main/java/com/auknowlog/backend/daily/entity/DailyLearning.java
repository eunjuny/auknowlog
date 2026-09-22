package com.auknowlog.backend.daily.entity;

import com.auknowlog.backend.source.entity.SourceDocument;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_learning")
public class DailyLearning {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate learningDate;
    @Column(nullable = false) private String articleTitle;
    @Column(nullable = false, length = 2048) private String articleUrl;
    private LocalDateTime articlePublishedAt;
    @Column(nullable = false, columnDefinition = "TEXT") private String articleSummary;
    @Column(nullable = false, columnDefinition = "TEXT") private String supplement;
    @Column(nullable = false, columnDefinition = "TEXT") private String concepts;
    @Column(nullable = false) private String reviewTopic;
    @Column(nullable = false) private int recommendedReviewQuestionCount;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_document_id")
    private SourceDocument sourceDocument;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DailyLearningStatus status;
    private LocalDateTime completedAt;
    @Column(length = 1000) private String generationError;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected DailyLearning() { }

    public DailyLearning(LocalDate learningDate, String articleTitle, String articleUrl, LocalDateTime articlePublishedAt,
                         String articleSummary, String supplement, String concepts, String reviewTopic,
                         int recommendedReviewQuestionCount, SourceDocument sourceDocument) {
        this.learningDate = learningDate;
        this.articleTitle = articleTitle;
        this.articleUrl = articleUrl;
        this.articlePublishedAt = articlePublishedAt;
        this.articleSummary = articleSummary;
        this.supplement = supplement;
        this.concepts = concepts;
        this.reviewTopic = reviewTopic;
        this.recommendedReviewQuestionCount = recommendedReviewQuestionCount;
        this.sourceDocument = sourceDocument;
        this.status = DailyLearningStatus.READY;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void complete() { this.status = DailyLearningStatus.COMPLETED; this.completedAt = LocalDateTime.now(); this.updatedAt = this.completedAt; }
    public Long getId() { return id; }
    public LocalDate getLearningDate() { return learningDate; }
    public String getArticleTitle() { return articleTitle; }
    public String getArticleUrl() { return articleUrl; }
    public LocalDateTime getArticlePublishedAt() { return articlePublishedAt; }
    public String getArticleSummary() { return articleSummary; }
    public String getSupplement() { return supplement; }
    public String getConcepts() { return concepts; }
    public String getReviewTopic() { return reviewTopic; }
    public int getRecommendedReviewQuestionCount() { return recommendedReviewQuestionCount; }
    public SourceDocument getSourceDocument() { return sourceDocument; }
    public DailyLearningStatus getStatus() { return status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
