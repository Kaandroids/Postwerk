package com.postwerk.exception;

/**
 * Thrown when a user with correct credentials tries to log in before confirming their email.
 *
 * <p>Handled by {@link GlobalExceptionHandler} as HTTP 403 with a {@code EMAIL_NOT_VERIFIED} code
 * (and the email) so the frontend can show a "verify your email / resend" prompt rather than a
 * generic credential error.</p>
 */
public class EmailNotVerifiedException extends RuntimeException {

    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Email address not verified");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
