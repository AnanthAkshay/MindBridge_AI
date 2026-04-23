package com.mindbridge.core.repository;

import com.mindbridge.core.entity.RiskLevel;
import com.mindbridge.core.entity.Session;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Session> findByStatus(String status);

    long countByUserId(Long userId);

    /**
     * Find the most recent N sessions for a given user, ordered by creation time descending.
     * Used by SessionBaselineAdjuster to evaluate recent session risk history.
     *
     * @param userId   the user whose sessions to retrieve
     * @param pageable page request controlling the number of results
     * @return list of recent sessions
     */
    List<Session> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Count how many of a user's sessions since the given timestamp have a specific risk level.
     * Used for baseline modifier calculation.
     *
     * @param userId    the user ID
     * @param riskLevel the risk level to count
     * @param since     only consider sessions updated after this timestamp
     * @return count of matching sessions
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.user.id = :userId " +
           "AND s.riskLevel = :riskLevel AND s.riskUpdatedAt >= :since")
    long countByUserIdAndRiskLevelSince(@Param("userId") Long userId,
                                        @Param("riskLevel") RiskLevel riskLevel,
                                        @Param("since") Instant since);

    /**
     * Find all sessions that have expired (past their TTL).
     * Used by the session expiry scheduled job.
     *
     * @param now the current timestamp
     * @return list of expired sessions
     */
    @Query("SELECT s FROM Session s WHERE s.expiresAt IS NOT NULL AND s.expiresAt < :now AND s.status <> 'DELETED'")
    List<Session> findExpiredSessions(@Param("now") Instant now);

    /**
     * Delete all sessions for a given user (GDPR deletion).
     *
     * @param userId the user ID
     */
    void deleteByUserId(Long userId);

    /**
     * Find sessions with null user (anonymous sessions).
     *
     * @return list of anonymous sessions
     */
    List<Session> findByUserIsNull();
}
