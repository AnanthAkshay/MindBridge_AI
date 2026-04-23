package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.AuditLog;
import com.mindbridge.core.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized audit logging service.
 *
 * <p>Logs sensitive operations to the {@code audit_log} table.
 * All log writes are asynchronous to avoid blocking the main
 * request path. Supported actions:</p>
 * <ul>
 *   <li>{@code USER_LOGIN} — successful login</li>
 *   <li>{@code USER_LOGOUT} — logout</li>
 *   <li>{@code TOKEN_REFRESH} — JWT token refresh</li>
 *   <li>{@code SESSION_CREATE} — new chat session created</li>
 *   <li>{@code GDPR_SELF_DELETE} — user self-deletion</li>
 *   <li>{@code ESCALATION_FIRED} — crisis escalation triggered</li>
 *   <li>{@code ANONYMOUS_LOGIN} — anonymous session created</li>
 *   <li>{@code SESSION_EXPIRED} — session soft-deleted by TTL job</li>
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    /**
     * Construct the audit service.
     *
     * @param auditLogRepository repository for audit log persistence
     */
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log an auditable event.
     *
     * @param action    the action identifier
     * @param actorId   the user who performed the action (null for anonymous)
     * @param targetId  the target entity ID
     * @param ipAddress the client IP address
     * @param userAgent the client user-agent string
     * @param metadata  JSON metadata string (nullable)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, Long actorId, Long targetId,
                     String ipAddress, String userAgent, String metadata) {
        try {
            AuditLog entry = new AuditLog(action, actorId, targetId,
                    ipAddress, userAgent, metadata);
            auditLogRepository.save(entry);

            logger.info("AUDIT: action={}, actorId={}, targetId={}, ip={}",
                    action, actorId, targetId, ipAddress);
        } catch (Exception e) {
            // Audit log failure must never crash the main request
            logger.error("Failed to write audit log: action={}, error={}",
                    action, e.getMessage());
        }
    }

    /**
     * Convenience method for logging without target ID.
     *
     * @param action    the action identifier
     * @param actorId   the actor user ID
     * @param ipAddress the client IP
     * @param userAgent the client user-agent
     */
    public void log(String action, Long actorId, String ipAddress, String userAgent) {
        log(action, actorId, null, ipAddress, userAgent, null);
    }

    /**
     * Convenience method for logging with just action and actor.
     *
     * @param action  the action identifier
     * @param actorId the actor user ID
     */
    public void log(String action, Long actorId) {
        log(action, actorId, null, null, null, null);
    }
}
