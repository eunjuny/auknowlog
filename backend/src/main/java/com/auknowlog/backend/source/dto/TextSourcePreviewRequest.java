package com.auknowlog.backend.source.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextSourcePreviewRequest(
        @NotBlank(message = "학습 자료 제목을 입력해주세요.")
        @Size(max = 255, message = "학습 자료 제목은 255자 이하여야 합니다.")
        String title,
        @NotBlank(message = "학습 자료 내용을 입력해주세요.")
        @Size(max = 50_000, message = "학습 자료 내용은 50,000자 이하여야 합니다.")
        String content
) {
}
