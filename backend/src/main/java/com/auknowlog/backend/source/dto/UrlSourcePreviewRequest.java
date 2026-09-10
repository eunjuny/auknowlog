package com.auknowlog.backend.source.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UrlSourcePreviewRequest(
        @NotBlank(message = "가져올 URL을 입력해주세요.")
        @Size(max = 2_048, message = "URL은 2,048자 이하여야 합니다.")
        String url
) {
}
