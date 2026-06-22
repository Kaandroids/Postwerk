package com.postwerk.exception;

/**
 * Thrown when an authentication or authorization operation fails.
 *
 * <p>Covers invalid credentials, expired sessions, and insufficient permissions.
 * Handled globally by {@link GlobalExceptionHandler} to return HTTP 401.</p>
 *
 * @since 1.0
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
