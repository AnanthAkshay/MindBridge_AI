package com.mindbridge.core.repository;

import com.mindbridge.core.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link AuditLog} entities.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit log entries by action type.
     *
     * @param action the action to filter by
     * @return list of matching audit entries
     */
    List<AuditLog> findByAction(String action);

    /**
     * Find all audit log entries by actor ID, newest first.
     *
     * @param actorId the actor user ID
     * @return list of audit entries for the actor
     */
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    /**
     * Check if an audit entry exists for a given action and actor.
     *
     * @param action  the action type
     * @param actorId the actor user ID
     * @return true if at least one matching entry exists
     */
    boolean existsByActionAndActorId(String action, Long actorId);
}
