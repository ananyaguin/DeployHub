package com.cloudflow.exception;

import java.time.Instant;

import com.cloudflow.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Request failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiError(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        HttpHeaders safeHeaders = headers == null ? new HttpHeaders() : headers;
        return new ResponseEntity<>(apiError(status), safeHeaders, status);
    }

    private static ApiError apiError(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String error = resolved == null ? "Error" : resolved.getReasonPhrase();
        return new ApiError(status.value(), error, publicMessage(status), Instant.now());
    }

    private static String publicMessage(HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "Resource not found";
        }
        if (status.is4xxClientError()) {
            return "Request could not be processed";
        }
        return "Unexpected error";
    }
}
