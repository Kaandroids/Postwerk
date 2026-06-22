package com.postwerk.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        filter = new RateLimitFilter(redisTemplate);
    }

    @Test
    void doFilter_loginEndpoint_allowsUnderLimit() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_loginEndpoint_blocks429WhenExceeded() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(21L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_registerEndpoint_blocksAt10Requests() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(11L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_aiEndpoint_blocksAt30Requests() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(31L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_syncEndpoint_blocksAt10Requests() throws Exception {
        var request = new MockHttpServletRequest("POST",
                "/api/v1/email-accounts/123/emails/sync");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(11L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_sendEndpoint_blocksAt50PerHour() throws Exception {
        var request = new MockHttpServletRequest("POST",
                "/api/v1/email-accounts/123/emails/send");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(51L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_sendEndpoint_allowsUnderLimit() throws Exception {
        var request = new MockHttpServletRequest("POST",
                "/api/v1/email-accounts/123/emails/send");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(50L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_nonLimitedEndpoint_alwaysAllows() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/email-accounts");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void doFilter_setsRateLimitHeaders() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(5L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("15");
    }

    @Test
    void doFilter_remainingHeaderDecrements() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(19L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
    }

    @Test
    void doFilter_differentIps_separateLimits() throws Exception {
        var request1 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request1.setRemoteAddr("1.1.1.1");
        var request2 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request2.setRemoteAddr("2.2.2.2");
        var response1 = new MockHttpServletResponse();
        var response2 = new MockHttpServletResponse();

        when(valueOps.increment(contains("1.1.1.1"))).thenReturn(21L);
        when(valueOps.increment(contains("2.2.2.2"))).thenReturn(1L);

        filter.doFilterInternal(request1, response1, filterChain);
        filter.doFilterInternal(request2, response2, filterChain);

        assertThat(response1.getStatus()).isEqualTo(429);
        assertThat(response2.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_xForwardedFor_extractsRightmostNonTrustedIp() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        // remoteAddr defaults to 127.0.0.1 (trusted proxy)
        request.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18, 150.172.238.178");
        var response = new MockHttpServletResponse();
        // IpResolverUtil takes rightmost non-trusted IP to prevent spoofing
        when(valueOps.increment(contains("150.172.238.178"))).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(valueOps).increment(contains("150.172.238.178"));
    }

    @Test
    void doFilter_noForwardedHeader_usesRemoteAddr() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.20.30.40");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(contains("10.20.30.40"))).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(valueOps).increment(contains("10.20.30.40"));
    }

    @Test
    void doFilter_firstRequest_setsExpiry() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(redisTemplate).expire(anyString(), any());
    }

    @Test
    void doFilter_returns429JsonBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(21L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("429");
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void doFilter_registerEndpoint_allowsAt10() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(10L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_refreshEndpoint_blocksAt30() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        when(valueOps.increment(anyString())).thenReturn(31L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
