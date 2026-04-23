package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.NotificationOutbox;
import com.mindbridge.core.entity.TherapistQueue;
import com.mindbridge.core.repository.NotificationOutboxRepository;
import com.mindbridge.core.repository.TherapistQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification service for escalation events.
 *
 * <p>On escalation, this service:</p>
 * <ol>
 *   <li>Inserts a row into {@code therapist_queue} for therapist review</li>
 *   <li>Writes simulated email and SMS entries to {@code notifications_outbox}
 *       (actual sending is out of scope — payloads are persisted for future dispatch)</li>
 * </ol>
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final TherapistQueueRepository therapistQueueRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Construct the notification service.
     *
     * @param therapistQueueRepository     repository for therapist queue entries
     * @param notificationOutboxRepository repository for notification outbox entries
     */
    public NotificationService(TherapistQueueRepository therapistQueueRepository,
                                NotificationOutboxRepository notificationOutboxRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.therapistQueueRepository = therapistQueueRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Process an escalation by creating therapist queue entry and notification payloads.
     *
     * <p>This method is called after an escalation log entry has been persisted.
     * It creates:</p>
     * <ul>
     *   <li>A pending therapist queue entry linked to the escalation</li>
     *   <li>A simulated email notification payload</li>
     *   <li>A simulated SMS notification payload</li>
     * </ul>
     *
     * @param escalationLog the persisted escalation log entry
     */
    @Transactional
    public void notify(EscalationLog escalationLog) {
        long startTime = System.currentTimeMillis();

        // 1. Create therapist queue entry
        TherapistQueue queueEntry = new TherapistQueue(escalationLog);
        therapistQueueRepository.save(queueEntry);
        logger.info("Therapist queue entry created: escalationId={}, queueId={}",
                escalationLog.getId(), queueEntry.getId());

        // 2. Simulate email notification
        String emailPayload = buildEmailPayload(escalationLog);
        NotificationOutbox emailNotification = new NotificationOutbox(
                escalationLog,
                "email",
                "oncall-therapist@mindbridge.ai",
                "[URGENT] Crisis Escalation — Session #" + escalationLog.getSession().getId(),
                emailPayload
        );
        notificationOutboxRepository.save(emailNotification);

        // 3. Simulate SMS notification
        String smsPayload = buildSmsPayload(escalationLog);
        NotificationOutbox smsNotification = new NotificationOutbox(
                escalationLog,
                "sms",
                "+1-555-CRISIS",
                null,
                smsPayload
        );
        notificationOutboxRepository.save(smsNotification);

        // 4. Send real-time WebSocket alert to therapists
        messagingTemplate.convertAndSend("/topic/escalations", escalationLog.getId());

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("NotificationService.notify completed in {}ms for escalation {}",
                elapsed, escalationLog.getId());
    }

    /**
     * Build an email notification payload for a crisis escalation.
     *
     * @param log the escalation log entry
     * @return formatted email body
     */
    private String buildEmailPayload(EscalationLog log) {
        return String.format(
                "CRISIS ESCALATION ALERT\n" +
                "========================\n" +
                "Session ID: %d\n" +
                "User ID: %s\n" +
                "Trigger Rule: %s\n" +
                "Risk Score: %d\n" +
                "Timestamp: %s\n" +
                "========================\n" +
                "Immediate therapist review required.\n" +
                "Helplines provided to user:\n" +
                "  - 988 Suicide & Crisis Lifeline\n" +
                "  - Crisis Text Line: text HOME to 741741",
                log.getSession().getId(),
                log.getUser() != null ? String.valueOf(log.getUser().getId()) : "anonymous",
                log.getTriggerRule(),
                log.getRiskScore(),
                log.getCreatedAt()
        );
    }

    /**
     * Build an SMS notification payload for a crisis escalation.
     *
     * @param log the escalation log entry
     * @return formatted SMS body
     */
    private String buildSmsPayload(EscalationLog log) {
        return String.format(
                "[MindBridge ALERT] Crisis escalation fired. Session #%d, " +
                "Rule: %s, Score: %d. Immediate review required.",
                log.getSession().getId(),
                log.getTriggerRule(),
                log.getRiskScore()
        );
    }
}
