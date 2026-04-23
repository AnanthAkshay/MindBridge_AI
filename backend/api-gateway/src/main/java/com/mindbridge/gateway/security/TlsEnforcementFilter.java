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
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * TLS enforcement filter — rejects non-HTTPS requests in production.
 *
 * <p>Behaviour by profile:</p>
 * <ul>
 *   <li><b>Production</b> ({@code tls.enforce=true}): Returns 301 redirect
 *       to HTTPS equivalent URL.</li>
 *   <li><b>Development</b> ({@code tls.enforce=false}, default): Logs a
 *       warning but allows the request through.</li>
 * </ul>
 *
 * <p>Always adds {@code Strict-Transport-Security} header with
 * max-age ≥ 31536000 (1 year) and {@code includeSubDomains}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TlsEnforcementFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TlsEnforcementFilter.class);

    /** HSTS max-age: 1 year (31536000 seconds). */
    private static final String HSTS_HEADER_VALUE = "max-age=31536000; includeSubDomains";

    private final boolean enforce;

    /**
     * Construct the TLS filter.
     *
     * @param enforce whether to enforce HTTPS redirects (default: false for dev)
     */
    public TlsEnforcementFilter(
            @Value("${mindbridge.tls.enforce:false}") boolean enforce) {
        this.enforce = enforce;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Always add HSTS header
        httpResp.setHeader("Strict-Transport-Security", HSTS_HEADER_VALUE);

        // Add security headers
        httpResp.setHeader("X-Content-Type-Options", "nosniff");
        httpResp.setHeader("X-Frame-Options", "DENY");
        httpResp.setHeader("X-XSS-Protection", "1; mode=block");

        boolean isSecure = httpReq.isSecure()
                || "https".equalsIgnoreCase(httpReq.getScheme())
                || "https".equalsIgnoreCase(httpReq.getHeader("X-Forwarded-Proto"));

        if (!isSecure) {
            if (enforce) {
                // Production: 301 redirect to HTTPS
                String httpsUrl = "https://" + httpReq.getServerName()
                        + httpReq.getRequestURI()
                        + (httpReq.getQueryString() != null ? "?" + httpReq.getQueryString() : "");

                httpResp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                httpResp.setHeader("Location", httpsUrl);
                logger.warn("TLS: Redirecting HTTP → HTTPS: {}", httpsUrl);
                return;
            } else {
                // Dev mode: log warning but allow
                logger.warn("TLS: Non-HTTPS request detected (dev mode — not enforcing): {} {}",
                        httpReq.getMethod(), httpReq.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }
}
