package com.postwerk.service;

import com.postwerk.dto.auth.*;
import com.postwerk.exception.AuthException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import java.time.Instant;
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
                       OrganizationService organizationService) {
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
    }

    public AuthResponse register(RegisterRequest request, String ipAddress) {
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

        auditService.log(user.getId(), AuditAction.USER_REGISTERED, ipAddress);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.create(user.getEmail());

        return new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpirationMs(), user.getRole().name());
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

    public void resetPasswordRequest(String email, String ipAddress) {
        User user = userRepository.findByEmail(email).orElse(null);
        auditService.log(
                user != null ? user.getId() : null,
                AuditAction.PASSWORD_RESET_REQUESTED,
                "Email: " + email,
                ipAddress
        );
        // Actual email sending not implemented — log only
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
