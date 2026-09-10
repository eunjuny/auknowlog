package com.auknowlog.backend.source.dto;

import com.auknowlog.backend.source.entity.SourceType;

import java.time.LocalDateTime;

public record SourceSummaryResponse(
        Long sourceId,
        String title,
        SourceType sourceType,
        String sourceUri,
        String originalName,
        String mimeType,
        int contentLength,
        int chunkCount,
        LocalDateTime createdAt
) {
}
