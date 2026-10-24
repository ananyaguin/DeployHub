package com.cloudflow.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Operation not permitted");
    }
}
