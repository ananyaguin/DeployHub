package com.cloudflow.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.cloudflow.dto.response.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return status(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return status(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return status(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return status(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("Request rejected by a database constraint");
        return status(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBinding(BindException ex) {
        return ResponseEntity.badRequest().body(apiError(HttpStatus.BAD_REQUEST, messages(ex)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> leaf(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .toList();
        return ResponseEntity.badRequest().body(apiError(HttpStatus.BAD_REQUEST, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Request failed", ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(apiError(HttpStatus.BAD_REQUEST, messages(ex)));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    String name = result.getMethodParameter().getParameterName();
                    String field = name == null ? "request" : name;
                    return result.getResolvableErrors().stream()
                            .map(error -> field + ": " + text(error.getDefaultMessage()));
                })
                .toList();
        return ResponseEntity.badRequest().body(apiError(HttpStatus.BAD_REQUEST, errors));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        HttpHeaders safeHeaders = headers == null ? new HttpHeaders() : headers;
        return new ResponseEntity<>(apiError(status, List.of()), safeHeaders, status);
    }

    private static ResponseEntity<ApiError> status(HttpStatus status) {
        return ResponseEntity.status(status).body(apiError(status, List.of()));
    }

    private static List<String> messages(BindException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError error : ex.getFieldErrors()) {
            errors.add(error.getField() + ": " + text(error.getDefaultMessage()));
        }
        for (ObjectError error : ex.getGlobalErrors()) {
            errors.add(error.getObjectName() + ": " + text(error.getDefaultMessage()));
        }
        return List.copyOf(errors);
    }

    private static String leaf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    private static String text(String message) {
        return message == null ? "is invalid" : message;
    }

    private static ApiError apiError(HttpStatusCode status, List<String> errors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String error = resolved == null ? "Error" : resolved.getReasonPhrase();
        return new ApiError(status.value(), error, publicMessage(status), Instant.now(), errors);
    }

    private static String publicMessage(HttpStatusCode status) {
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
