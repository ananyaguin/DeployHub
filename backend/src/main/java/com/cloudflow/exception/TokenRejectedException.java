package com.cloudflow.exception;

public class TokenRejectedException extends RuntimeException {

    public TokenRejectedException() {
        super("Request could not be processed");
    }
}
