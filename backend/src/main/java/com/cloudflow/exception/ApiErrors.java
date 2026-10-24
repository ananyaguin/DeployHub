package com.cloudflow.exception;

import java.time.Instant;
import java.util.List;

import com.cloudflow.dto.response.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public final class ApiErrors {

    private ApiErrors() {}

    public static ApiError of(HttpStatus status) {
        return of(status, message(status), List.of());
    }

    public static ApiError of(HttpStatus status, String message) {
        return of(status, message, List.of());
    }

    public static ApiError of(HttpStatusCode status, List<String> errors) {
        return of(status, message(status), errors);
    }

    private static ApiError of(HttpStatusCode status, String message, List<String> errors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String phrase = resolved == null ? "Error" : resolved.getReasonPhrase();
        return new ApiError(status.value(), phrase, message, Instant.now(), errors);
    }

    public static String message(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return "Request could not be processed";
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return "Authentication required";
        }
        if (status.value() == HttpStatus.FORBIDDEN.value()) {
            return "Operation not permitted";
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "Resource not found";
        }
        if (status.value() == HttpStatus.CONFLICT.value()) {
            return "Resource already exists";
        }
        if (status.is4xxClientError()) {
            return "Request could not be processed";
        }
        return "Unexpected error";
    }
}
