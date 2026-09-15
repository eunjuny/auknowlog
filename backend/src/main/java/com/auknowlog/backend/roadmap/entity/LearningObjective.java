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

@Entity
@Table(name = "learning_objective")
public class LearningObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_step_id", nullable = false)
    private LearningRoadmapStep roadmapStep;

    @Column(nullable = false, length = 48)
    private String objectiveKey;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 16)
    private String importance;

    @Column(nullable = false)
    private int targetQuestionCount;

    @Column(nullable = false)
    private int objectiveOrder;

    protected LearningObjective() {
    }

    public LearningObjective(LearningRoadmapStep roadmapStep, String objectiveKey, String title,
                             String description, String importance, int targetQuestionCount,
                             int objectiveOrder) {
        this.roadmapStep = roadmapStep;
        this.objectiveKey = objectiveKey;
        this.title = title;
        this.description = description;
        this.importance = importance;
        this.targetQuestionCount = targetQuestionCount;
        this.objectiveOrder = objectiveOrder;
    }

    public Long getId() { return id; }
    public LearningRoadmapStep getRoadmapStep() { return roadmapStep; }
    public String getObjectiveKey() { return objectiveKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImportance() { return importance; }
    public int getTargetQuestionCount() { return targetQuestionCount; }
    public int getObjectiveOrder() { return objectiveOrder; }
}
