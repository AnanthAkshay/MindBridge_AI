package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.RefreshTokenRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GDPR compliance controller — handles user self-deletion.
 *
 * <p>{@code DELETE /api/user/me} performs a full data purge:</p>
 * <ol>
 *   <li>Hard-delete all messages for all user sessions</li>
 *   <li>Delete all sessions</li>
 *   <li>Delete all refresh tokens</li>
 *   <li>Delete the user record</li>
 *   <li>Write an audit log entry</li>
 * </ol>
 *
 * <p>Returns {@code 204 No Content} on success. This operation is
 * irreversible and complies with GDPR Article 17 (Right to Erasure).</p>
 */
@RestController
@RequestMapping("/api/user")
public class GdprController {

    private static final Logger logger = LoggerFactory.getLogger(GdprController.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    /**
     * Construct the GDPR controller.
     *
     * @param userRepository         user data access
     * @param sessionRepository      session data access
     * @param messageRepository      message data access
     * @param refreshTokenRepository refresh token data access
     * @param auditService           audit logging service
     */
    public GdprController(UserRepository userRepository,
                           SessionRepository sessionRepository,
                           MessageRepository messageRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    /**
     * Delete all data for the authenticated user (GDPR Article 17).
     *
     * <p>This endpoint permanently deletes:</p>
     * <ul>
     *   <li>All encrypted message content (hard delete)</li>
     *   <li>All chat sessions</li>
     *   <li>All refresh tokens</li>
     *   <li>The user account</li>
     * </ul>
     *
     * @param user    the authenticated user (injected by Spring Security)
     * @param request the HTTP request for audit metadata
     * @return 204 No Content on success
     */
    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<Void> deleteMyData(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {

        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Long userId = user.getId();
        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        logger.warn("GDPR SELF-DELETE initiated: userId={}", userId);

        // 1. Hard-delete all messages (encrypted content included)
        messageRepository.deleteAllByUserId(userId);
        logger.info("GDPR: Deleted all messages for userId={}", userId);

        // 2. Delete all sessions
        sessionRepository.deleteByUserId(userId);
        logger.info("GDPR: Deleted all sessions for userId={}", userId);

        // 3. Delete all refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);
        logger.info("GDPR: Revoked all tokens for userId={}", userId);

        // 4. Delete user record
        userRepository.deleteById(userId);
        logger.info("GDPR: Deleted user record for userId={}", userId);

        // 5. Write audit log
        String metadata = String.format(
                "{\"action\":\"USER_SELF_DELETE\",\"userId\":%d,\"timestamp\":\"%s\"}",
                userId, java.time.Instant.now().toString());
        auditService.log("GDPR_SELF_DELETE", userId, userId, ipAddress, userAgent, metadata);

        logger.warn("GDPR SELF-DELETE completed: userId={}", userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Extract real client IP respecting proxy headers.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
