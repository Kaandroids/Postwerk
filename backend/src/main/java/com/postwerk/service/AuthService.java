package com.postwerk.service;

import com.postwerk.dto.auth.*;
import com.postwerk.exception.AuthException;
import com.postwerk.exception.EmailNotVerifiedException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Core authentication service handling user registration, login, token refresh, logout,
 * and password reset workflows. Integrates rate limiting, audit logging, and JWT token management.
 *
 * @since 1.0
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final AuditService auditService;
    private final LoginRateLimitService rateLimitService;
    private final OrganizationService organizationService;
    private final VerificationTokenService verificationTokenService;
    private final AuthMailService authMailService;
    private final WizardService wizardService;

    public AuthService(UserRepository userRepository,
                       PlanRepository planRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       TokenBlacklistService tokenBlacklistService,
                       AuthenticationManager authenticationManager,
                       CustomUserDetailsService userDetailsService,
                       AuditService auditService,
                       LoginRateLimitService rateLimitService,
                       OrganizationService organizationService,
                       VerificationTokenService verificationTokenService,
                       AuthMailService authMailService,
                       WizardService wizardService) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.auditService = auditService;
        this.rateLimitService = rateLimitService;
        this.organizationService = organizationService;
        this.verificationTokenService = verificationTokenService;
        this.authMailService = authMailService;
        this.wizardService = wizardService;
    }

    /**
     * Registers a new account in the UNVERIFIED state. No tokens are issued — the user must confirm
     * their email before they can log in. A wizard session (from the /getstarted flow) is claimed
     * server-side here, while it is still fresh in Redis, so its automation survives the (possibly
     * long) gap until the user verifies.
     */
    public RegisterResponse register(RegisterRequest request, String ipAddress) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("Registration failed. Please try again or use a different email.");
        }

        Instant now = Instant.now();
        var user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .company(request.company())
                .phone(request.phone())
                .emailVerified(false)
                .marketingOptIn(request.marketingOptIn())
                .privacyAcceptedAt(now)
                .termsAcceptedAt(now)
                .privacyVersion("2026-05")
                .marketingOptedInAt(request.marketingOptIn() ? now : null)
                .build();

        planRepository.findByName(Plan.DEFAULT_PLAN_NAME).ifPresent(user::setPlan);
        userRepository.save(user);

        // Multi-tenant (#4): every new user gets a personal workspace (OWNER) so all their
        // resources have an owning organization from day one.
        organizationService.provisionPersonalOrg(user);

        // Claim the wizard session now (best-effort) — see method javadoc for why this happens at
        // register time rather than after login.
        claimWizardSession(request.wizardSessionId(), user.getId(), ipAddress);

        auditService.log(user.getId(), AuditAction.USER_REGISTERED, ipAddress);

        // Send the verification link. Best-effort inside AuthMailService; the user can resend.
        String token = verificationTokenService.createVerificationToken(user.getId());
        authMailService.sendVerificationEmail(user, token, request.lang());

        return new RegisterResponse(true, user.getEmail());
    }

    /**
     * Materializes a /getstarted wizard session's resources into the freshly created account.
     * Best-effort: any failure (session expired, IP mismatch, not in "ready" phase) is logged and
     * swallowed so it never blocks registration — the user still gets their account.
     */
    private void claimWizardSession(String wizardSessionId, UUID userId, String ipAddress) {
        if (wizardSessionId == null || wizardSessionId.isBlank()) {
            return;
        }
        try {
            UUID sessionId = UUID.fromString(wizardSessionId.trim());
            wizardService.claimSession(sessionId, userId, ipAddress);
        } catch (Exception e) {
            log.warn("Wizard claim during registration failed for user {} (session {}): {}",
                    userId, wizardSessionId, e.getMessage());
        }
    }

    public AuthResponse login(LoginRequest request, String ipAddress) {
        String rateLimitKey = ipAddress + ":" + request.email();

        // Check rate limit before attempting auth
        if (rateLimitService.isLockedOut(rateLimitKey)) {
            long remainingSec = rateLimitService.getRemainingLockoutSeconds(rateLimitKey);
            // Log the locked-out attempt
            User lockedUser = userRepository.findByEmail(request.email()).orElse(null);
            auditService.log(
                    lockedUser != null ? lockedUser.getId() : null,
                    AuditAction.LOGIN_LOCKED_OUT,
                    "Email: " + request.email() + ", remaining: " + remainingSec + "s",
                    ipAddress
            );
            throw new AuthException("Too many login attempts. Try again in " + remainingSec + " seconds.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            // Record failed attempt
            int attempts = rateLimitService.recordFailedAttempt(rateLimitKey);
            // Also rate limit by IP alone (prevents trying different emails from same IP)
            rateLimitService.recordFailedAttempt(ipAddress);

            User failedUser = userRepository.findByEmail(request.email()).orElse(null);
            auditService.log(
                    failedUser != null ? failedUser.getId() : null,
                    AuditAction.LOGIN_FAILED,
                    "Email: " + request.email() + ", attempt: " + attempts + "/" + rateLimitService.getMaxAttempts(),
                    ipAddress
            );

            throw new AuthException("Invalid email or password");
        }

        // Success — clear rate limit counters
        rateLimitService.clearAttempts(rateLimitKey);
        rateLimitService.clearAttempts(ipAddress);

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException("User not found"));

        // Gate: the account must have a confirmed email before it can log in. Credentials were
        // already verified above, so this is a distinct, recoverable state (resend verification).
        if (!user.isEmailVerified()) {
            auditService.log(user.getId(), AuditAction.USER_LOGIN,
                    "Blocked: email not verified", ipAddress);
            throw new EmailNotVerifiedException(user.getEmail());
        }

        // Update last login info
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        auditService.log(user.getId(), AuditAction.USER_LOGIN, ipAddress);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.create(request.email());

        return new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpirationMs(), user.getRole().name());
    }

    public AuthResponse refresh(RefreshRequest request, String ipAddress) {
        String email = refreshTokenService.validate(request.refreshToken());
        if (email == null) {
            throw new AuthException("Invalid or expired refresh token");
        }

        refreshTokenService.revoke(request.refreshToken());

        // Log token refresh
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            auditService.log(user.getId(), AuditAction.TOKEN_REFRESHED, ipAddress);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newRefreshToken = refreshTokenService.create(email);

        String role = user != null ? user.getRole().name() : "USER";
        return new AuthResponse(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpirationMs(), role);
    }

    /**
     * Confirms an email address from a verification token and logs the user in (issues tokens).
     * The token is single-use (consumed from Redis).
     */
    public AuthResponse verifyEmail(String token, String ipAddress) {
        UUID userId = verificationTokenService.consumeVerificationToken(token);
        if (userId == null) {
            throw new AuthException("This verification link is invalid or has expired.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("This verification link is invalid or has expired."));

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            userRepository.save(user);
            auditService.log(user.getId(), AuditAction.EMAIL_VERIFIED, ipAddress);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.create(user.getEmail());
        return new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpirationMs(), user.getRole().name());
    }

    /**
     * Re-sends the verification email to an unverified account. Always returns normally (no signal
     * of whether the email exists) and is throttled by a short per-email cooldown.
     */
    public void resendVerification(String email, String lang, String ipAddress) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isEmailVerified() || verificationTokenService.isResendOnCooldown(email)) {
            return;
        }
        verificationTokenService.markResendSent(email);
        String token = verificationTokenService.createVerificationToken(user.getId());
        authMailService.sendVerificationEmail(user, token, lang);
        auditService.log(user.getId(), AuditAction.VERIFICATION_EMAIL_RESENT, ipAddress);
    }

    /**
     * Initiates a password reset by emailing a single-use reset link. Always returns normally so it
     * never reveals whether an account exists for the given address.
     */
    public void resetPasswordRequest(String email, String lang, String ipAddress) {
        User user = userRepository.findByEmail(email).orElse(null);
        auditService.log(
                user != null ? user.getId() : null,
                AuditAction.PASSWORD_RESET_REQUESTED,
                "Email: " + email,
                ipAddress
        );
        if (user == null) {
            return;
        }
        String token = verificationTokenService.createResetToken(user.getId());
        authMailService.sendPasswordResetEmail(user, token, lang);
    }

    /**
     * Completes a password reset: validates the single-use token, sets the new password, and revokes
     * all existing refresh tokens (sessions) for safety.
     */
    public void resetPassword(String token, String newPassword, String ipAddress) {
        UUID userId = verificationTokenService.consumeResetToken(token);
        if (userId == null) {
            throw new AuthException("This password reset link is invalid or has expired.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("This password reset link is invalid or has expired."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // A reset implicitly proves email ownership — verify the account if it wasn't already.
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
        }
        userRepository.save(user);

        // Invalidate all active sessions — a reset should log out everywhere.
        refreshTokenService.revokeAllForUser(user.getEmail());
        auditService.log(user.getId(), AuditAction.PASSWORD_RESET_COMPLETED, ipAddress);
    }

    public void logout(String accessToken, String refreshToken, String ipAddress) {
        if (accessToken != null) {
            try {
                String email = jwtService.extractEmail(accessToken);
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    auditService.log(user.getId(), AuditAction.USER_LOGOUT, ipAddress);
                }
                String jti = jwtService.extractJti(accessToken);
                long remainingTtl = jwtService.getRemainingTtlMs(accessToken);
                tokenBlacklistService.blacklist(jti, remainingTtl);
            } catch (Exception ignored) {
                // Token may already be expired, that's fine
            }
        }
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
    }
}
