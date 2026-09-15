package com.auknowlog.backend.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RemoteAccessMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsTheCurrentQuickTunnelCredentialsOnlyToTheConfiguredRecipient() {
        RemoteAccessMailService service = new RemoteAccessMailService(mailSender, configuredProperties());
        RemoteAccessMailRequest request = validRequest();

        service.send(request);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("sender@example.test");
        assertThat(message.getTo()).containsExactly("receiver@example.test");
        assertThat(message.getSubject()).contains("Auknowlog");
        assertThat(message.getText())
                .contains(request.publicUrl())
                .contains(request.username())
                .contains(request.password());
    }

    @Test
    void rejectsNonQuickTunnelUrlsBeforeAttemptingDelivery() {
        RemoteAccessMailService service = new RemoteAccessMailService(mailSender, configuredProperties());
        RemoteAccessMailRequest unsafeRequest = new RemoteAccessMailRequest(
                "https://example.com", "tester", "0123456789abcdef0123456789abcdef", OffsetDateTime.now().plusHours(1)
        );

        assertThatThrownBy(() -> service.send(unsafeRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quick Tunnel");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void doesNotAttemptDeliveryWhenMailSecretsAreMissing() {
        RemoteAccessMailService service = new RemoteAccessMailService(
                mailSender, new RemoteAccessMailProperties("smtp.gmail.com", 587, "", "", "")
        );

        assertThatThrownBy(() -> service.send(validRequest()))
                .isInstanceOf(RemoteAccessMailUnavailableException.class)
                .hasMessageContaining("메일 설정");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    private RemoteAccessMailProperties configuredProperties() {
        return new RemoteAccessMailProperties(
                "smtp.gmail.com", 587, "sender@example.test", "app-password", "receiver@example.test"
        );
    }

    private RemoteAccessMailRequest validRequest() {
        return new RemoteAccessMailRequest(
                "https://example.trycloudflare.com",
                "tester",
                "0123456789abcdef0123456789abcdef",
                OffsetDateTime.now().plusHours(8)
        );
    }
}
