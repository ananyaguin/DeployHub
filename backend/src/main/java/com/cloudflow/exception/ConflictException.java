package com.cloudflow.exception;

public class ConflictException extends RuntimeException {

    public ConflictException() {
        super("Resource already exists");
    }
}
