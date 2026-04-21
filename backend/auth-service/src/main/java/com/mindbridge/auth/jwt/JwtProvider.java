package com.mindbridge.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT token creation and validation utility.
 * Uses HMAC-SHA256 signing via JJWT 0.12.x builder API.
 */
@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpMs;
    private final long refreshTokenExpMs;

    public JwtProvider(
            @Value("${mindbridge.jwt.secret}") String secret,
            @Value("${mindbridge.jwt.access-token-expiration-ms:900000}") long accessTokenExpMs,
            @Value("${mindbridge.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpMs
    ) {
        // Pad to at least 32 bytes for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpMs = accessTokenExpMs;
        this.refreshTokenExpMs = refreshTokenExpMs;
    }

    /** Generate a short-lived access token */
    public String generateAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email != null ? email : "")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpMs)))
                .signWith(signingKey)
                .compact();
    }

    /** Generate an opaque refresh token string (UUID-based for DB storage) */
    public String generateRefreshTokenValue() {
        return java.util.UUID.randomUUID().toString();
    }

    /** Get refresh token expiration instant */
    public Instant getRefreshTokenExpiry() {
        return Instant.now().plusMillis(refreshTokenExpMs);
    }

    /** Extract userId (subject) from a valid access token */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /** Extract role from token */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /** Validate token — returns true if structurally valid and not expired */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
