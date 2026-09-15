package com.auknowlog.backend.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(RemoteAccessMailProperties.class)
class RemoteAccessMailConfiguration {

    @Bean
    JavaMailSender remoteAccessMailSender(RemoteAccessMailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.appPassword());

        Properties sessionProperties = sender.getJavaMailProperties();
        sessionProperties.put("mail.transport.protocol", "smtp");
        sessionProperties.put("mail.smtp.auth", "true");
        sessionProperties.put("mail.smtp.starttls.enable", "true");
        sessionProperties.put("mail.smtp.starttls.required", "true");
        sessionProperties.put("mail.smtp.connectiontimeout", "10000");
        sessionProperties.put("mail.smtp.timeout", "10000");
        sessionProperties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
