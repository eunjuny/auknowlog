package com.auknowlog.backend.notification;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record RemoteAccessMailRequest(
        @NotBlank(message = "Quick Tunnel URL이 필요합니다.")
        @Pattern(
                regexp = "^https://[a-z0-9-]+\\.trycloudflare\\.com/?$",
                message = "Cloudflare Quick Tunnel URL만 메일로 전송할 수 있습니다."
        )
        String publicUrl,

        @NotBlank(message = "원격 접속 사용자명이 필요합니다.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "원격 접속 사용자명 형식이 올바르지 않습니다.")
        String username,

        @NotBlank(message = "원격 접속 비밀번호가 필요합니다.")
        @Size(min = 20, max = 128, message = "원격 접속 비밀번호 길이가 올바르지 않습니다.")
        String password,

        @NotNull(message = "터널 만료 시간이 필요합니다.")
        @Future(message = "터널 만료 시간은 현재보다 이후여야 합니다.")
        OffsetDateTime expiresAt
) {
}
