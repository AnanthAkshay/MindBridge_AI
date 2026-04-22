package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.Session;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job for session expiry — GDPR §17 data retention compliance.
 *
 * <p>Runs daily and processes sessions that have exceeded their TTL:</p>
 * <ol>
 *   <li>Hard-deletes all messages for expired sessions</li>
 *   <li>Soft-deletes the session (sets status to "DELETED")</li>
 *   <li>Writes an audit log entry for each expired session</li>
 * </ol>
 *
 * <p>The default TTL is configurable via the {@code SESSION_TTL_DAYS}
 * environment variable (default: 90 days).</p>
 */
@Component
public class SessionExpiryJob {

    private static final Logger logger = LoggerFactory.getLogger(SessionExpiryJob.class);

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final AuditService auditService;
    private final int ttlDays;

    /**
     * Construct the session expiry job.
     *
     * @param sessionRepository session data access
     * @param messageRepository message data access (for hard-delete)
     * @param auditService      audit logging
     * @param ttlDays           session TTL in days (default 90)
     */
    public SessionExpiryJob(
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            AuditService auditService,
            @Value("${mindbridge.session.ttl-days:${SESSION_TTL_DAYS:90}}") int ttlDays) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.auditService = auditService;
        this.ttlDays = ttlDays;

        logger.info("SessionExpiryJob initialized: TTL={} days", ttlDays);
    }

    /**
     * Get the configured TTL in days.
     *
     * @return session TTL in days
     */
    public int getTtlDays() {
        return ttlDays;
    }

    /**
     * Run the session expiry job daily at 02:00 AM.
     *
     * <p>Finds all sessions with {@code expires_at < now} and
     * status ≠ "DELETED", hard-deletes their messages, and
     * soft-deletes the sessions.</p>
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireSessions() {
        logger.info("SessionExpiryJob started");
        long startTime = System.currentTimeMillis();

        List<Session> expiredSessions = sessionRepository.findExpiredSessions(Instant.now());

        if (expiredSessions.isEmpty()) {
            logger.info("SessionExpiryJob: No expired sessions found");
            return;
        }

        int processed = 0;
        for (Session session : expiredSessions) {
            try {
                // 1. Hard-delete all messages (GDPR §17 — no plaintext recovery)
                messageRepository.deleteBySessionId(session.getId());

                // 2. Soft-delete the session
                session.setStatus("DELETED");
                session.setEndedAt(Instant.now());
                sessionRepository.save(session);

                // 3. Audit log
                Long userId = session.getUser() != null ? session.getUser().getId() : null;
                String metadata = String.format(
                        "{\"sessionId\":%d,\"ttlDays\":%d,\"expiresAt\":\"%s\"}",
                        session.getId(), ttlDays,
                        session.getExpiresAt() != null ? session.getExpiresAt().toString() : "null");
                auditService.log("SESSION_EXPIRED", null, session.getId(),
                        null, null, metadata);

                processed++;
            } catch (Exception e) {
                logger.error("Failed to expire session {}: {}", session.getId(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("SessionExpiryJob completed: expired={} sessions, elapsed={}ms",
                processed, elapsed);
    }

    /**
     * Manually trigger expiry (for testing).
     *
     * @return number of sessions expired
     */
    public int expireSessionsNow() {
        expireSessions();
        return sessionRepository.findExpiredSessions(Instant.now()).size();
    }
}
