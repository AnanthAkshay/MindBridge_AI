package com.mindbridge.gateway.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Token bucket rate limiter using Redis for stateless-friendly persistence.
 *
 * <p>Implements a sliding-window counter pattern using Redis INCR + EXPIRE:</p>
 * <ul>
 *   <li><b>Per-IP</b>: 60 requests per minute</li>
 *   <li><b>Per-user</b>: 120 requests per minute (identified by JWT subject)</li>
 * </ul>
 *
 * <p>When a limit is exceeded, returns {@code 429 Too Many Requests} with
 * a {@code Retry-After} header indicating how many seconds to wait.</p>
 *
 * <p>Uses the existing Redis stack (already in the dependency tree from
 * {@code spring-boot-starter-data-redis}) — no new packages needed.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimiterFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterFilter.class);

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final int perIpLimit;
    private final int perUserLimit;
    private final boolean enabled;

    /**
     * Construct the rate limiter filter.
     *
     * @param redisTemplate Spring Redis template for counter storage
     * @param perIpLimit    max requests per IP per minute (default 60)
     * @param perUserLimit  max requests per user per minute (default 120)
     * @param enabled       whether rate limiting is active (default true)
     */
    public RateLimiterFilter(
            StringRedisTemplate redisTemplate,
            @Value("${mindbridge.rate-limit.per-ip:60}") int perIpLimit,
            @Value("${mindbridge.rate-limit.per-user:120}") int perUserLimit,
            @Value("${mindbridge.rate-limit.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.perIpLimit = perIpLimit;
        this.perUserLimit = perUserLimit;
        this.enabled = enabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // 1. Per-IP rate limiting
        String clientIp = extractClientIp(httpReq);
        String ipKey = RATE_LIMIT_PREFIX + "ip:" + clientIp;

        if (isRateLimited(ipKey, perIpLimit, httpResp)) {
            return; // 429 already sent
        }

        // 2. Per-user rate limiting (if authenticated)
        String userId = extractUserId(httpReq);
        if (userId != null) {
            String userKey = RATE_LIMIT_PREFIX + "user:" + userId;
            if (isRateLimited(userKey, perUserLimit, httpResp)) {
                return; // 429 already sent
            }
        }

        // Add rate limit headers
        httpResp.setHeader("X-RateLimit-Limit-IP", String.valueOf(perIpLimit));
        if (userId != null) {
            httpResp.setHeader("X-RateLimit-Limit-User", String.valueOf(perUserLimit));
        }

        chain.doFilter(request, response);
    }

    /**
     * Check and increment the counter for a rate limit key.
     * If the limit is exceeded, sends a 429 response and returns true.
     *
     * @param key      the Redis key for this rate limit bucket
     * @param limit    the maximum allowed requests in the window
     * @param response the HTTP response (used to send 429)
     * @return true if rate limited (429 sent), false if allowed
     */
    private boolean isRateLimited(String key, int limit, HttpServletResponse response)
            throws IOException {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // First request in window — set TTL
                redisTemplate.expire(key, WINDOW);
            }

            if (count != null && count > limit) {
                Long ttl = redisTemplate.getExpire(key);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : 60;

                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Too Many Requests\",\"retryAfter\":" + retryAfter + "}");

                logger.warn("Rate limit exceeded: key={}, count={}, limit={}", key, count, limit);
                return true;
            }
        } catch (Exception e) {
            // Redis failure should not block requests — degrade gracefully
            logger.error("Rate limiter Redis error (allowing request): {}", e.getMessage());
        }

        return false;
    }

    /**
     * Extract the real client IP, respecting proxy headers.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Extract user ID from the request attributes (set by JWT filter).
     *
     * @param request the HTTP request
     * @return the user ID string, or null if not authenticated
     */
    private String extractUserId(HttpServletRequest request) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mindbridge.core.entity.User user) {
            return String.valueOf(user.getId());
        }
        return null;
    }
}
