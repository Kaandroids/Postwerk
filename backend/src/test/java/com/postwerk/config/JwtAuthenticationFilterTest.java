package com.postwerk.config;

import com.postwerk.service.CustomUserDetailsService;
import com.postwerk.service.JwtService;
import com.postwerk.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        testUser = new User("test@example.com", "pass", Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_validToken_setsAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractEmail("valid-token")).thenReturn("test@example.com");
        when(jwtService.extractJti("valid-token")).thenReturn("jti-123");
        when(tokenBlacklistService.isBlacklisted("jti-123")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(testUser);
        when(jwtService.isTokenValid("valid-token", testUser)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("test@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_missingAuthHeader_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_malformedHeader_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_expiredToken_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        when(jwtService.extractEmail("expired-token")).thenThrow(new RuntimeException("Token expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_blacklistedToken_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer blacklisted-token");
        when(jwtService.extractEmail("blacklisted-token")).thenReturn("test@example.com");
        when(jwtService.extractJti("blacklisted-token")).thenReturn("jti-blacklisted");
        when(tokenBlacklistService.isBlacklisted("jti-blacklisted")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidSignature_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-signature");
        when(jwtService.extractEmail("bad-signature")).thenThrow(new RuntimeException("Invalid signature"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_validToken_setsCorrectUserDetails() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractEmail("valid-token")).thenReturn("test@example.com");
        when(jwtService.extractJti("valid-token")).thenReturn("jti-123");
        when(tokenBlacklistService.isBlacklisted("jti-123")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(testUser);
        when(jwtService.isTokenValid("valid-token", testUser)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(testUser);
        assertThat(auth.getCredentials()).isNull();
    }

    @Test
    void doFilter_nullBearer_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(jwtService.extractEmail("")).thenThrow(new RuntimeException("Empty token"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_emptyToken_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_userNotFound_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractEmail("valid-token")).thenReturn("notfound@example.com");
        when(jwtService.extractJti("valid-token")).thenReturn("jti-123");
        when(tokenBlacklistService.isBlacklisted("jti-123")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("notfound@example.com"))
                .thenThrow(new RuntimeException("User not found"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidToken_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractEmail("valid-token")).thenReturn("test@example.com");
        when(jwtService.extractJti("valid-token")).thenReturn("jti-123");
        when(tokenBlacklistService.isBlacklisted("jti-123")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(testUser);
        when(jwtService.isTokenValid("valid-token", testUser)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_nullEmail_continuesWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractEmail("valid-token")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }
}
