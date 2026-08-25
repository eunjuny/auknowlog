package com.auknowlog.backend.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_generation_log")
public class AiGenerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String status;

    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;

    @Column(nullable = false)
    private long latencyMs;

    private String failureType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AiGenerationLog() {
    }

    public AiGenerationLog(String operation, String model, String status, Long inputTokens, Long outputTokens,
                           Long totalTokens, long latencyMs, String failureType) {
        this.operation = operation;
        this.model = model;
        this.status = status;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.failureType = failureType;
        this.createdAt = LocalDateTime.now();
    }
}
