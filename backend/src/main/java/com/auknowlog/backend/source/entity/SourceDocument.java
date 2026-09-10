package com.auknowlog.backend.source.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "source_document")
public class SourceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @Column(name = "source_uri", length = 2048)
    private String sourceUri;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "content_hash", length = 64, unique = true)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private SourceProcessingStatus processingStatus;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SourceDocument() {
    }

    public SourceDocument(String title, String content) {
        this(title, content, SourceType.TEXT, null, null, "text/plain", null);
    }

    public SourceDocument(String title,
                          String content,
                          SourceType sourceType,
                          String sourceUri,
                          String originalName,
                          String mimeType,
                          String contentHash) {
        this.title = title;
        this.content = content;
        this.sourceType = sourceType;
        this.sourceUri = sourceUri;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.contentHash = contentHash;
        this.processingStatus = SourceProcessingStatus.READY;
        this.fetchedAt = sourceType == SourceType.URL ? LocalDateTime.now() : null;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public SourceProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
