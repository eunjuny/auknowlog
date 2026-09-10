package com.auknowlog.backend.source.dto;

import com.auknowlog.backend.source.entity.SourceType;

import java.util.List;

public record SourceRoadmapContext(
        Long sourceId,
        String title,
        SourceType sourceType,
        String sourceUri,
        List<SourceChunkContext> chunks
) {
    public int contentLength() {
        return chunks.stream().mapToInt(chunk -> chunk.content().length()).sum();
    }
}
