package com.postwerk.service;

import com.postwerk.config.JwtProperties;
import com.postwerk.model.enums.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String TEST_SECRET = "this-is-a-very-long-test-secret-key-for-hmac-sha256-signing-minimum-32-bytes";
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 900_000;   // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000; // 7 days
    private static final String TEST_EMAIL = "user@example.com";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                TEST_SECRET,
                ACCESS_TOKEN_EXPIRATION_MS,
                REFRESH_TOKEN_EXPIRATION_MS
        );
        jwtService = new JwtService(properties, new MockEnvironment());
    }

    // ─── generateAccessToken ──────────────────────────────────────

    @Test
    void generateAccessToken_returnsNonNullToken() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateAccessToken_containsEmailAsSubject() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractEmail(token)).isEqualTo(TEST_EMAIL);
    }

    @Test
    void generateAccessToken_containsRoleClaim() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.ADMIN);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void generateAccessToken_hasCorrectExpiration() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);
        long beforeMs = System.currentTimeMillis();

        String token = jwtService.generateAccessToken(userDetails);

        long afterMs = System.currentTimeMillis();
        Date expiration = jwtService.extractExpiration(token);

        // Allow 2 second tolerance for rounding and execution time
        assertThat(expiration.getTime())
                .isBetween(beforeMs + ACCESS_TOKEN_EXPIRATION_MS - 2000, afterMs + ACCESS_TOKEN_EXPIRATION_MS + 2000);
    }

    @Test
    void generateAccessToken_isNotExpired() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    // ─── extractEmail ─────────────────────────────────────────────

    @Test
    void extractEmail_returnsCorrectEmail() {
        UserDetails userDetails = createUserDetails("admin@postwerk.de", Role.ADMIN);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractEmail(token)).isEqualTo("admin@postwerk.de");
    }

    // ─── extractRole ──────────────────────────────────────────────

    @Test
    void extractRole_userRole_returnsUSER() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractRole(token)).isEqualTo("USER");
    }

    @Test
    void extractRole_adminRole_returnsADMIN() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.ADMIN);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    // ─── extractJti ───────────────────────────────────────────────

    @Test
    void extractJti_returnsNonNullUniqueId() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractJti(token)).isNotNull().isNotBlank();
    }

    @Test
    void extractJti_differentTokensHaveDifferentIds() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token1 = jwtService.generateAccessToken(userDetails);
        String token2 = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.extractJti(token1)).isNotEqualTo(jwtService.extractJti(token2));
    }

    // ─── isTokenValid ─────────────────────────────────────────────

    @Test
    void isTokenValid_validToken_returnsTrue() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_differentUser_returnsFalse() {
        UserDetails tokenOwner = createUserDetails(TEST_EMAIL, Role.USER);
        UserDetails otherUser = createUserDetails("other@example.com", Role.USER);

        String token = jwtService.generateAccessToken(tokenOwner);

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtException() {
        // Build an already-expired token directly
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(TEST_EMAIL)
                .claim("role", "USER")
                .issuer("postwerk")
                .audience().add("postwerk-api").and()
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        // The parser rejects expired tokens before isTokenValid can check
        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, userDetails))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void isTokenValid_tamperedToken_throwsException() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);
        String token = jwtService.generateAccessToken(userDetails);

        // Tamper with the token by modifying a character in the signature
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> jwtService.isTokenValid(tampered, userDetails))
                .isInstanceOf(Exception.class);
    }

    @Test
    void isTokenValid_tokenSignedWithDifferentKey_throwsException() {
        // Create a service with a different secret
        JwtProperties otherProps = new JwtProperties(
                "another-very-long-secret-key-for-hmac-sha256-at-least-32-bytes-long",
                ACCESS_TOKEN_EXPIRATION_MS,
                REFRESH_TOKEN_EXPIRATION_MS
        );
        JwtService otherService = new JwtService(otherProps, new MockEnvironment());
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String tokenFromOtherService = otherService.generateAccessToken(userDetails);

        assertThatThrownBy(() -> jwtService.isTokenValid(tokenFromOtherService, userDetails))
                .isInstanceOf(Exception.class);
    }

    // ─── isTokenExpired ───────────────────────────────────────────

    @Test
    void isTokenExpired_freshToken_returnsFalse() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    void isTokenExpired_expiredToken_throwsExpiredJwtException() {
        // Build an already-expired token directly
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(TEST_EMAIL)
                .claim("role", "USER")
                .issuer("postwerk")
                .audience().add("postwerk-api").and()
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        // The parser throws before isTokenExpired can evaluate
        assertThatThrownBy(() -> jwtService.isTokenExpired(expiredToken))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    // ─── getRemainingTtlMs ────────────────────────────────────────

    @Test
    void getRemainingTtlMs_freshToken_returnsPositiveValue() {
        UserDetails userDetails = createUserDetails(TEST_EMAIL, Role.USER);

        String token = jwtService.generateAccessToken(userDetails);

        long remainingMs = jwtService.getRemainingTtlMs(token);
        assertThat(remainingMs).isPositive().isLessThanOrEqualTo(ACCESS_TOKEN_EXPIRATION_MS);
    }

    // ─── getAccessTokenExpirationMs ───────────────────────────────

    @Test
    void getAccessTokenExpirationMs_returnsConfiguredValue() {
        assertThat(jwtService.getAccessTokenExpirationMs()).isEqualTo(ACCESS_TOKEN_EXPIRATION_MS);
    }

    // ─── token with different roles ───────────────────────────────

    @Test
    void tokenWithDifferentRoles_userAndAdmin_storedCorrectly() {
        UserDetails userUser = createUserDetails("user@test.com", Role.USER);
        UserDetails adminUser = createUserDetails("admin@test.com", Role.ADMIN);

        String userToken = jwtService.generateAccessToken(userUser);
        String adminToken = jwtService.generateAccessToken(adminUser);

        assertThat(jwtService.extractRole(userToken)).isEqualTo("USER");
        assertThat(jwtService.extractRole(adminToken)).isEqualTo("ADMIN");
        assertThat(jwtService.extractEmail(userToken)).isEqualTo("user@test.com");
        assertThat(jwtService.extractEmail(adminToken)).isEqualTo("admin@test.com");
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private UserDetails createUserDetails(String email, Role role) {
        return new User(
                email,
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
