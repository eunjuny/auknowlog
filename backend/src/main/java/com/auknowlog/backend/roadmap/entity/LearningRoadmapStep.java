package com.auknowlog.backend.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "learning_roadmap_step")
public class LearningRoadmapStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private LearningRoadmap roadmap;

    @Column(nullable = false, length = 64)
    private String stepKey;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private int questionTarget;

    @Column(nullable = false)
    private int stepOrder;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "learning_roadmap_step_dependency",
            joinColumns = @JoinColumn(name = "step_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_step_id")
    )
    private Set<LearningRoadmapStep> prerequisites = new LinkedHashSet<>();

    protected LearningRoadmapStep() {
    }

    public LearningRoadmapStep(LearningRoadmap roadmap, String stepKey, String title, String description,
                               String topic, int questionTarget, int stepOrder) {
        this.roadmap = roadmap;
        this.stepKey = stepKey;
        this.title = title;
        this.description = description;
        this.topic = topic;
        this.questionTarget = questionTarget;
        this.stepOrder = stepOrder;
    }

    public void addPrerequisite(LearningRoadmapStep prerequisite) {
        prerequisites.add(prerequisite);
    }

    public Long getId() { return id; }
    public LearningRoadmap getRoadmap() { return roadmap; }
    public String getStepKey() { return stepKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTopic() { return topic; }
    public int getQuestionTarget() { return questionTarget; }
    public int getStepOrder() { return stepOrder; }
    public Set<LearningRoadmapStep> getPrerequisites() { return prerequisites; }
}
