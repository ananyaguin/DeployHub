package com.cloudflow.exception;

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
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrors.of(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return status(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrors.of(HttpStatus.UNAUTHORIZED, "Authentication failed"));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return status(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiError> handleUnverified(EmailNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrors.of(HttpStatus.FORBIDDEN, "Email verification required"));
    }

    @ExceptionHandler(TokenRejectedException.class)
    public ResponseEntity<ApiError> handleToken(TokenRejectedException ex) {
        return status(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MailDeliveryException.class)
    public ResponseEntity<ApiError> handleMail(MailDeliveryException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrors.of(HttpStatus.SERVICE_UNAVAILABLE, "Email could not be sent"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("Request rejected by a database constraint");
        return status(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBinding(BindException ex) {
        return ResponseEntity.badRequest().body(ApiErrors.of(HttpStatus.BAD_REQUEST, messages(ex)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> leaf(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .toList();
        return ResponseEntity.badRequest().body(ApiErrors.of(HttpStatus.BAD_REQUEST, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Request failed", ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(ApiErrors.of(HttpStatus.BAD_REQUEST, messages(ex)));
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
        return ResponseEntity.badRequest().body(ApiErrors.of(HttpStatus.BAD_REQUEST, errors));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        HttpHeaders safeHeaders = headers == null ? new HttpHeaders() : headers;
        return new ResponseEntity<>(ApiErrors.of(status, List.of()), safeHeaders, status);
    }

    private static ResponseEntity<ApiError> status(HttpStatus status) {
        return ResponseEntity.status(status).body(ApiErrors.of(status));
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
}
