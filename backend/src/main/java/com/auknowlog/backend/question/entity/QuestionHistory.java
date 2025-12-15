package com.auknowlog.backend.question.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_history", indexes = {
    @Index(name = "idx_question_hash", columnList = "questionHash", unique = true),
    @Index(name = "idx_topic", columnList = "topic")
})
public class QuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, length = 64, unique = true)
    private String questionHash;  // SHA-256 해시

    @Column(columnDefinition = "TEXT")
    private String options;  // JSON 문자열로 저장

    @Column
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 기본 생성자
    public QuestionHistory() {}

    // 생성자
    public QuestionHistory(String topic, String questionText, String questionHash, 
                          String options, String correctAnswer, String explanation) {
        this.topic = topic;
        this.questionText = questionText;
        this.questionHash = questionHash;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionHash() { return questionHash; }
    public void setQuestionHash(String questionHash) { this.questionHash = questionHash; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

