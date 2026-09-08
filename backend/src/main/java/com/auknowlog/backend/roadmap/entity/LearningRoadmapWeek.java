package com.auknowlog.backend.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "learning_roadmap_week")
public class LearningRoadmapWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private LearningRoadmap roadmap;

    @Column(nullable = false)
    private int weekNumber;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private LocalDate weekEnd;

    @Column(nullable = false)
    private int plannedQuestions;

    protected LearningRoadmapWeek() {
    }

    public LearningRoadmapWeek(LearningRoadmap roadmap, int weekNumber, String topic,
                               LocalDate weekStart, LocalDate weekEnd, int plannedQuestions) {
        this.roadmap = roadmap;
        this.weekNumber = weekNumber;
        this.topic = topic;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.plannedQuestions = plannedQuestions;
    }

    public Long getId() {
        return id;
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    public String getTopic() {
        return topic;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public int getPlannedQuestions() {
        return plannedQuestions;
    }
}
