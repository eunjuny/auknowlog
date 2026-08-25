package com.auknowlog.backend.source.entity;

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
@Table(name = "source_chunk")
public class SourceChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false)
    private SourceDocument sourceDocument;

    @Column(name = "chunk_order", nullable = false)
    private int chunkOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    protected SourceChunk() {
    }

    public SourceChunk(SourceDocument sourceDocument, int chunkOrder, String content) {
        this.sourceDocument = sourceDocument;
        this.chunkOrder = chunkOrder;
        this.content = content;
    }

    public int getChunkOrder() {
        return chunkOrder;
    }

    public String getContent() {
        return content;
    }
}
