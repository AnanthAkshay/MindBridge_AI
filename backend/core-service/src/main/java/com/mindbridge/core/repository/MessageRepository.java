package com.mindbridge.core.repository;

import com.mindbridge.core.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    /**
     * Find the most recent messages in a session filtered by sender type,
     * ordered by creation time descending (newest first).
     * Used by EscalationEngine to check consecutive high-risk USER messages.
     *
     * @param sessionId  the session to query
     * @param senderType the sender type filter (e.g. "USER")
     * @param pageable   page request controlling the number of results
     * @return list of recent messages (newest first)
     */
    List<Message> findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
            Long sessionId, String senderType, Pageable pageable);
}
