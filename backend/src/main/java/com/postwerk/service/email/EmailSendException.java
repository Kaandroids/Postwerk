package com.postwerk.service.email;

/** Thrown when an {@link EmailSender} fails to dispatch a message to the mail transport. */
public class EmailSendException extends RuntimeException {
    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
