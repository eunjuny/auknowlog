package com.auknowlog.backend.source.dto;

import com.auknowlog.backend.source.entity.SourceType;

public record SourceCreateResponse(
        Long sourceId,
        String title,
        int chunkCount,
        SourceType sourceType,
        boolean reused
) {
}
