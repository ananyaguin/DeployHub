package com.cloudflow.exception;

public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException() {
        super("Email could not be sent");
    }
}
