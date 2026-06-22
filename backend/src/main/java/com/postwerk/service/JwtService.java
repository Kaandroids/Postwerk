package com.postwerk.service;

import com.postwerk.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service for JWT access token generation, validation, and claim extraction.
 * Handles HMAC-based signing, expiration checks, and JTI management.
 *
 * @since 1.0
 */
@Service
public class JwtService {

    /** The well-known shipped dev placeholder JWT secret — rejected under 'prod'. */
    private static final String DEV_SECRET = "change-this-to-a-random-string-at-least-32-bytes-long";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties, Environment environment) {
        this.jwtProperties = jwtProperties;
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && DEV_SECRET.equals(jwtProperties.secret())) {
            throw new IllegalStateException(
                    "JWT_SECRET is still set to the insecure shipped dev placeholder under the 'prod' profile. "
                            + "Generate a real secret with: openssl rand -base64 48");
        }
        byte[] keyBytes = jwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer("postwerk")
                .audience().add("postwerk-api").and()
                .subject(userDetails.getUsername())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.accessTokenExpirationMs()))
                .signWith(signingKey)
                .compact();
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public long getRemainingTtlMs(String token) {
        Date expiration = extractExpiration(token);
        return expiration.getTime() - System.currentTimeMillis();
    }

    public long getAccessTokenExpirationMs() {
        return jwtProperties.accessTokenExpirationMs();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer("postwerk")
                .requireAudience("postwerk-api")
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}
