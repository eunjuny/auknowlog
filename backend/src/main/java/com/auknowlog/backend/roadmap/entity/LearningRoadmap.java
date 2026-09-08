package com.auknowlog.backend.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_roadmap")
public class LearningRoadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int durationWeeks;

    @Column(nullable = false)
    private int questionsPerWeek;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String sourceType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String definitionJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected LearningRoadmap() {
    }

    public LearningRoadmap(String title, String topic, LocalDate startDate, int durationWeeks, int questionsPerWeek) {
        this(title, topic, null, "WEEKLY", null, startDate, durationWeeks, questionsPerWeek);
    }

    public LearningRoadmap(String title, String topic, String description, String sourceType, String definitionJson,
                           LocalDate startDate, int durationWeeks, int questionsPerWeek) {
        this.title = title;
        this.topic = topic;
        this.description = description;
        this.sourceType = sourceType;
        this.definitionJson = definitionJson;
        this.startDate = startDate;
        this.endDate = startDate.plusWeeks(durationWeeks).minusDays(1);
        this.durationWeeks = durationWeeks;
        this.questionsPerWeek = questionsPerWeek;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    public void archive() {
        this.status = "ARCHIVED";
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getTopic() {
        return topic;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getDurationWeeks() {
        return durationWeeks;
    }

    public int getQuestionsPerWeek() {
        return questionsPerWeek;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getDescription() {
        return description;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
