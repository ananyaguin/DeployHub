package com.cloudflow.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflow.mail")
public record MailProperties(
        String host,
        int port,
        String username,
        String password,
        String from,
        String verificationUrl,
        String resetUrl,
        Duration verificationTtl,
        Duration resetTtl) {

    public MailProperties {
        host = host == null ? "" : host;
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("SMTP_PORT is invalid");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("SMTP_FROM is required");
        }
        if (verificationUrl == null || verificationUrl.isBlank() || resetUrl == null || resetUrl.isBlank()) {
            throw new IllegalStateException("Mail links are required");
        }
        if (verificationTtl == null || verificationTtl.isZero() || verificationTtl.isNegative()) {
            throw new IllegalStateException("MAIL_VERIFICATION_TTL must be positive");
        }
        if (resetTtl == null || resetTtl.isZero() || resetTtl.isNegative()) {
            throw new IllegalStateException("MAIL_RESET_TTL must be positive");
        }
    }
}
