package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimiterFilter}.
 *
 * <p>Validates per-IP rate limiting with Redis, 429 response
 * generation, and Retry-After header inclusion.</p>
 */
class RateLimiterFilterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RateLimiterFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.getExpire(anyString())).thenReturn(60L);

        filter = new RateLimiterFilter(redisTemplate, 60, 120, true);
    }

    /**
     * AT4: Exceed rate limit → confirm 429 response with Retry-After header.
     */
    @Test
    @DisplayName("AT4: Exceed rate limit → 429 with Retry-After")
    void exceedRateLimit_returns429WithRetryAfter() throws IOException, ServletException {
        // Simulate 61st request (exceeds 60/min IP limit)
        when(valueOps.increment(anyString())).thenReturn(61L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too Many Requests"));
    }

    @Test
    @DisplayName("Request within limit → passes through")
    void withinLimit_passesThrough() throws IOException, ServletException {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(429, response.getStatus());
    }

    @Test
    @DisplayName("Rate limit headers are set on allowed requests")
    void allowedRequest_hasRateLimitHeaders() throws IOException, ServletException {
        when(valueOps.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("60", response.getHeader("X-RateLimit-Limit-IP"));
    }

    @Test
    @DisplayName("Redis failure degrades gracefully — allows request")
    void redisFailure_allowsRequest() throws IOException, ServletException {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotEquals(429, response.getStatus(), "Should not block on Redis failure");
    }

    @Test
    @DisplayName("Disabled rate limiter passes all requests")
    void disabled_passesAllRequests() throws IOException, ServletException {
        RateLimiterFilter disabledFilter = new RateLimiterFilter(redisTemplate, 60, 120, false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabledFilter.doFilter(request, response, chain);

        // Should not even touch Redis
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("X-Forwarded-For header is respected for IP extraction")
    void xForwardedFor_respected() throws IOException, ServletException {
        when(valueOps.increment(contains("10.0.0.5"))).thenReturn(61L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.5, 192.168.1.1");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Should use 10.0.0.5 (first IP from X-Forwarded-For)
        verify(valueOps).increment(contains("10.0.0.5"));
    }
}
