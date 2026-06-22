package com.postwerk.dto.auth;

/**
 * Response DTO for registration. Unlike login, registration no longer issues tokens — the account
 * starts unverified and the user must confirm their email before logging in.
 *
 * @param verificationRequired always true in the current flow; signals the FE to show "check your email"
 * @param email                the address the verification link was sent to
 */
public record RegisterResponse(
        boolean verificationRequired,
        String email
) {}
