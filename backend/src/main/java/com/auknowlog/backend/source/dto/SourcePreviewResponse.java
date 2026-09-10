package com.auknowlog.backend.source.dto;

import com.auknowlog.backend.source.entity.SourceType;

public record SourcePreviewResponse(
        SourceType sourceType,
        String title,
        String content,
        String sourceUri,
        String originalName,
        String mimeType,
        int contentLength,
        int estimatedChunkCount,
        String contentHash,
        boolean duplicate,
        Long existingSourceId
) {
}
