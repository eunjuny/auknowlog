package com.auknowlog.backend.learning.entity;

import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.roadmap.entity.LearningRoadmap;
import com.auknowlog.backend.roadmap.entity.LearningRoadmapStep;
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
@Table(name = "learning_quiz")
public class LearningQuiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private SourceDocument sourceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    private LearningRoadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_step_id")
    private LearningRoadmapStep roadmapStep;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected LearningQuiz() {
    }

    public LearningQuiz(SourceDocument sourceDocument, String topic, String title) {
        this(sourceDocument, null, topic, title);
    }

    public LearningQuiz(SourceDocument sourceDocument, LearningRoadmap roadmap, String topic, String title) {
        this(sourceDocument, roadmap, null, topic, title);
    }

    public LearningQuiz(SourceDocument sourceDocument, LearningRoadmap roadmap, LearningRoadmapStep roadmapStep,
                        String topic, String title) {
        this.sourceDocument = sourceDocument;
        this.roadmap = roadmap;
        this.roadmapStep = roadmapStep;
        this.topic = topic;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getTitle() {
        return title;
    }

    public LearningRoadmap getRoadmap() {
        return roadmap;
    }

    public LearningRoadmapStep getRoadmapStep() {
        return roadmapStep;
    }
}
