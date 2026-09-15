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
import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 64)
    private String majorTopicKey;

    @Column(nullable = false)
    private String majorTopicTitle;

    @Column(columnDefinition = "TEXT")
    private String majorTopicDescription;

    @Column(nullable = false)
    private String majorTopicTopic;

    @Column(length = 48)
    private String subtopicKey;

    private String subtopicTitle;

    /** 목표 문항을 채운 뒤 사용자가 다음 단계 진행을 명시적으로 승인한 시각이다. */
    private LocalDateTime advanceConfirmedAt;

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
        this(roadmap, stepKey, title, description, topic, questionTarget, stepOrder,
                stepKey, title, description, topic, null, null);
    }

    public LearningRoadmapStep(LearningRoadmap roadmap, String stepKey, String title, String description,
                               String topic, int questionTarget, int stepOrder,
                               String majorTopicKey, String majorTopicTitle, String majorTopicDescription,
                               String majorTopicTopic, String subtopicKey, String subtopicTitle) {
        this.roadmap = roadmap;
        this.stepKey = stepKey;
        this.title = title;
        this.description = description;
        this.topic = topic;
        this.questionTarget = questionTarget;
        this.stepOrder = stepOrder;
        this.majorTopicKey = majorTopicKey;
        this.majorTopicTitle = majorTopicTitle;
        this.majorTopicDescription = majorTopicDescription;
        this.majorTopicTopic = majorTopicTopic;
        this.subtopicKey = subtopicKey;
        this.subtopicTitle = subtopicTitle;
    }

    public void addPrerequisite(LearningRoadmapStep prerequisite) {
        prerequisites.add(prerequisite);
    }

    public void confirmAdvance() {
        if (advanceConfirmedAt == null) {
            advanceConfirmedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public LearningRoadmap getRoadmap() { return roadmap; }
    public String getStepKey() { return stepKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTopic() { return topic; }
    public int getQuestionTarget() { return questionTarget; }
    public int getStepOrder() { return stepOrder; }
    public String getMajorTopicKey() { return majorTopicKey; }
    public String getMajorTopicTitle() { return majorTopicTitle; }
    public String getMajorTopicDescription() { return majorTopicDescription; }
    public String getMajorTopicTopic() { return majorTopicTopic; }
    public String getSubtopicKey() { return subtopicKey; }
    public String getSubtopicTitle() { return subtopicTitle; }
    public LocalDateTime getAdvanceConfirmedAt() { return advanceConfirmedAt; }
    public Set<LearningRoadmapStep> getPrerequisites() { return prerequisites; }
}
