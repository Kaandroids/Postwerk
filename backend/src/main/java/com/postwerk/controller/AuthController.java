package com.postwerk.controller;

import com.postwerk.dto.auth.*;
import com.postwerk.service.AuthService;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller handling user registration, login, token refresh, logout,
 * and password reset requests.
 *
 * <p>All endpoints are publicly accessible (no Bearer token required). JWT tokens are
 * issued on successful login/register and refreshed via the refresh endpoint.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Registers a new user account (unverified) and emails a verification link. No tokens are issued. */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.register(request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Confirms an email address from a verification token and returns JWT tokens (auto-login). */
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verifyEmail(request.token(), IpResolverUtil.extractIp(httpRequest)));
    }

    /** Re-sends the verification email to an unverified account (throttled; always 200). */
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                                              HttpServletRequest httpRequest) {
        authService.resendVerification(request.email(), request.lang(), IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("If an account exists, a verification email has been sent."));
    }

    /** Authenticates a user by email/password and returns JWT tokens. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Issues a new access token using a valid refresh token. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Invalidates the current access and refresh tokens. */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                   @RequestBody(required = false) RefreshRequest request,
                                                   HttpServletRequest httpRequest) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        String refreshToken = request != null ? request.refreshToken() : null;
        authService.logout(accessToken, refreshToken, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    /** Initiates a password reset flow by sending a reset email (always 200; no account-existence leak). */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@RequestBody @Valid ResetPasswordRequest request,
                                                          HttpServletRequest httpRequest) {
        authService.resetPasswordRequest(request.email(), request.lang(), IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("If an account exists, a password reset email has been sent."));
    }

    /** Completes a password reset using the emailed token and a new password. */
    @PostMapping("/reset-password/confirm")
    public ResponseEntity<MessageResponse> confirmReset(@RequestBody @Valid PasswordResetConfirmRequest request,
                                                        HttpServletRequest httpRequest) {
        authService.resetPassword(request.token(), request.newPassword(), IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully."));
    }
}
