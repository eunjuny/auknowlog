package com.auknowlog.backend.notification;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class RemoteAccessMailService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm z", Locale.KOREAN)
            .withZone(KOREA_ZONE);

    private final JavaMailSender mailSender;
    private final RemoteAccessMailProperties properties;

    public RemoteAccessMailService(JavaMailSender mailSender, RemoteAccessMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public RemoteAccessMailResponse send(RemoteAccessMailRequest request) {
        if (!properties.isConfigured()) {
            throw new RemoteAccessMailUnavailableException(
                    "원격 접속 메일 설정이 없습니다. 앱 비밀번호와 발신·수신 주소를 확인해주세요."
            );
        }
        if (!isQuickTunnelUrl(request.publicUrl())) {
            throw new IllegalArgumentException("Cloudflare Quick Tunnel URL만 메일로 전송할 수 있습니다.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.username());
        message.setTo(properties.recipient());
        message.setSubject("[Auknowlog] 임시 원격 접속 정보");
        message.setText(renderBody(request));

        try {
            mailSender.send(message);
            return new RemoteAccessMailResponse(OffsetDateTime.now());
        } catch (MailException exception) {
            throw new RemoteAccessMailUnavailableException(
                    "원격 접속 정보 메일을 전송하지 못했습니다. Gmail 앱 비밀번호와 SMTP 설정을 확인해주세요.",
                    exception
            );
        }
    }

    static boolean isQuickTunnelUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && host.matches("[a-z0-9-]+\\.trycloudflare\\.com")
                    && (uri.getRawPath() == null || uri.getRawPath().isBlank() || "/".equals(uri.getRawPath()))
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String renderBody(RemoteAccessMailRequest request) {
        return "Auknowlog 임시 원격 접속 정보입니다.\n\n"
                + "접속 주소\n" + request.publicUrl() + "\n\n"
                + "사용자명\n" + request.username() + "\n\n"
                + "비밀번호\n" + request.password() + "\n\n"
                + "만료 예정\n" + EXPIRY_FORMAT.format(request.expiresAt()) + "\n\n"
                + "이 메일과 인증 정보는 개인용 임시 접속 수단입니다. 필요 없으면 터널을 종료하고 메일을 삭제해주세요.";
    }
}
