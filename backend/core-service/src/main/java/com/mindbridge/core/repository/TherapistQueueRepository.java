package com.mindbridge.core.repository;

import com.mindbridge.core.entity.TherapistQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link TherapistQueue} entities.
 */
@Repository
public interface TherapistQueueRepository extends JpaRepository<TherapistQueue, Long> {

    /**
     * Find all queue entries by status.
     *
     * @param status the queue status filter (pending, reviewed, closed)
     * @return list of matching queue entries
     */
    List<TherapistQueue> findByStatus(String status);

    /**
     * Find queue entry by escalation ID.
     *
     * @param escalationId the escalation log ID
     * @return list of queue entries for the escalation
     */
    List<TherapistQueue> findByEscalationId(Long escalationId);
}
