package com.cloudflow.exception;

public class ConflictException extends RuntimeException {

    public ConflictException() {
        this("Resource already exists");
    }

    public ConflictException(String message) {
        super(message);
    }
}
