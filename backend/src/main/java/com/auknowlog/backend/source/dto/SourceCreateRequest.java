package com.auknowlog.backend.source.dto;

import com.auknowlog.backend.source.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SourceCreateRequest(
        @NotBlank(message = "학습 자료 제목을 입력해주세요.")
        @Size(max = 255, message = "학습 자료 제목은 255자 이하여야 합니다.")
        String title,
        @NotBlank(message = "학습 자료 내용을 입력해주세요.")
        @Size(max = 50_000, message = "학습 자료 내용은 50,000자 이하여야 합니다.")
        String content,
        SourceType sourceType,
        @Size(max = 2_048, message = "출처 URL은 2,048자 이하여야 합니다.")
        String sourceUri,
        @Size(max = 255, message = "원본 파일명은 255자 이하여야 합니다.")
        String originalName,
        @Size(max = 128, message = "MIME 타입은 128자 이하여야 합니다.")
        String mimeType
) {
}
