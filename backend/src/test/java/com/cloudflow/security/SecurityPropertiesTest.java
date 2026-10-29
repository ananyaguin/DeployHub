package com.cloudflow.security;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityPropertiesTest {

    @Test
    void blankJwtSecretIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> properties(" "));
        assertEquals("JWT_SECRET is required", error.getMessage());
    }

    @Test
    void wildcardOriginIsRejected() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("change-me-to-a-random-32-byte-secret", Duration.ofMinutes(15)),
                true,
                "http://localhost:5173,*",
                "");
        IllegalStateException error = assertThrows(IllegalStateException.class, properties::allowedOrigins);
        assertEquals("CORS_ALLOWED_ORIGINS must list explicit origins", error.getMessage());
    }

    private static SecurityProperties properties(String secret) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, Duration.ofMinutes(15)),
                true,
                "http://localhost:5173",
                "");
    }
}
