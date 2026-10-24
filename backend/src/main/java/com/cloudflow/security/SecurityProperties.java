package com.cloudflow.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflow.security")
public record SecurityProperties(
        Jwt jwt, boolean requireVerifiedEmail, String corsAllowedOrigins, String workerToken) {

    public record Jwt(String secret, Duration expiration) {}

    public SecurityProperties {
        if (jwt == null || jwt.secret() == null || jwt.secret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        if (jwt.expiration() == null || jwt.expiration().isZero() || jwt.expiration().isNegative()) {
            throw new IllegalStateException("JWT_EXPIRATION must be positive");
        }
        corsAllowedOrigins = corsAllowedOrigins == null ? "" : corsAllowedOrigins;
        workerToken = workerToken == null ? "" : workerToken;
    }

    public List<String> allowedOrigins() {
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list explicit origins");
        }
        return origins;
    }
}
