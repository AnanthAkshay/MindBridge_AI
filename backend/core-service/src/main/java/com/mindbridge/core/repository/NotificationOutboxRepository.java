package com.mindbridge.core.repository;

import com.mindbridge.core.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link NotificationOutbox} entities.
 */
@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    /**
     * Find all unsent notifications for future batch dispatch.
     *
     * @return list of unsent notification entries
     */
    List<NotificationOutbox> findBySentFalse();
}
