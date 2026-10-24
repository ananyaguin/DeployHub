package com.cloudflow.dto.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> errors) {

    public ApiError(int status, String error, String message, Instant timestamp) {
        this(status, error, message, timestamp, List.of());
    }

    public ApiError {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
