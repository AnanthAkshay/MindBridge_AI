package com.mindbridge.core.repository;

import com.mindbridge.core.entity.EscalationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link EscalationLog} entities.
 *
 * <p>Provides queries for deduplication checks, user-level
 * escalation history, and active escalation lookups.</p>
 */
@Repository
public interface EscalationLogRepository extends JpaRepository<EscalationLog, Long> {

    /**
     * Check if there is an active (un-resolved) escalation for a session.
     * Used for deduplication — prevents duplicate escalation fires.
     *
     * @param sessionId the session to check
     * @return true if an active escalation exists
     */
    boolean existsBySessionIdAndIsActiveTrue(Long sessionId);

    /**
     * Find all escalation logs for a given user, ordered by most recent first.
     *
     * @param userId the user ID
     * @return list of escalation log entries
     */
    List<EscalationLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Count active (un-resolved) escalations for a user.
     *
     * @param userId the user ID
     * @return count of active escalations
     */
    long countByUserIdAndIsActiveTrue(Long userId);

    /**
     * Find active escalation for a specific session (at most one expected).
     *
     * @param sessionId the session ID
     * @return list of active escalations (should be 0 or 1)
     */
    List<EscalationLog> findBySessionIdAndIsActiveTrue(Long sessionId);

    /**
     * Find all active escalations across all users (for therapist queue).
     *
     * @return list of active escalations
     */
    List<EscalationLog> findByIsActiveTrueOrderByCreatedAtDesc();
}
