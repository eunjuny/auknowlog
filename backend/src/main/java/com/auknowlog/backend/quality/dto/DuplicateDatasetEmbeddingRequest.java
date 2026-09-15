package com.auknowlog.backend.quality.dto;

import jakarta.validation.constraints.AssertTrue;

public record DuplicateDatasetEmbeddingRequest(
        @AssertTrue(message = "임베딩 API 비용 발생에 동의해야 합니다.")
        boolean confirmCost
) {
}
