package com.auknowlog.backend.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 원격 접속 정보를 본인에게만 발송하기 위한 SMTP 설정이다.
 * 민감한 실제 값은 Git에서 제외된 application-api.properties 또는 환경 변수로 주입한다.
 */
@ConfigurationProperties(prefix = "auknowlog.mail")
public record RemoteAccessMailProperties(
        @DefaultValue("smtp.gmail.com") String host,
        @DefaultValue("587") int port,
        String username,
        String appPassword,
        String recipient
) {

    public boolean isConfigured() {
        return hasText(username) && hasText(appPassword) && hasText(recipient);
    }

    @Override
    public String username() {
        return username == null ? "" : username.trim();
    }

    @Override
    public String appPassword() {
        // Gmail 화면은 16자리 앱 비밀번호를 네 자리씩 띄워 표시할 수 있다.
        // SMTP에는 공백 없는 값을 전달하며 원본은 어디에도 로그로 남기지 않는다.
        return appPassword == null ? "" : appPassword.replaceAll("\\s+", "");
    }

    @Override
    public String recipient() {
        return recipient == null ? "" : recipient.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
