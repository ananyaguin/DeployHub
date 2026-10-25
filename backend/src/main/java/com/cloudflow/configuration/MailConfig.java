package com.cloudflow.configuration;

import java.util.Properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    @Conditional(MailHostConfigured.class)
    JavaMailSender javaMailSender(MailProperties mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        if (!mail.username().isBlank()) {
            sender.setUsername(mail.username());
            sender.setPassword(mail.password());
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", Boolean.toString(!mail.username().isBlank()));
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        return sender;
    }

    public static final class MailHostConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String host = context.getEnvironment().getProperty("cloudflow.mail.host");
            return host != null && !host.isBlank();
        }
    }
}
