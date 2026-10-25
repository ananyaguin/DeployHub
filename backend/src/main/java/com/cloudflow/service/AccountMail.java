package com.cloudflow.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.cloudflow.configuration.MailProperties;
import com.cloudflow.exception.MailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class AccountMail {

    private static final Logger log = LoggerFactory.getLogger(AccountMail.class);

    private final MailProperties properties;
    private final ObjectProvider<JavaMailSender> senders;

    public AccountMail(MailProperties properties, ObjectProvider<JavaMailSender> senders) {
        this.properties = properties;
        this.senders = senders;
    }

    public Duration verificationTtl() {
        return properties.verificationTtl();
    }

    public Duration resetTtl() {
        return properties.resetTtl();
    }

    public void sendVerification(String to, String rawToken) {
        send(to, "Verify your CloudFlow email", link(properties.verificationUrl(), rawToken));
    }

    public void sendPasswordReset(String to, String rawToken) {
        send(to, "Reset your CloudFlow password", link(properties.resetUrl(), rawToken));
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) {
            log.warn("Email delivery failed");
            throw new MailDeliveryException();
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException ex) {
            log.warn("Email delivery failed");
            throw new MailDeliveryException();
        }
    }

    private static String link(String base, String rawToken) {
        String encoded = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return base + (base.contains("?") ? "&" : "?") + "token=" + encoded;
    }
}
