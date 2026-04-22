package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.Message;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.repository.EscalationLogRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Escalation engine that fires when risk thresholds are breached.
 *
 * <p>This is a stateless service that reads session/message history
 * from the database to determine whether an escalation should fire.
 * It implements two trigger rules:</p>
 *
 * <table>
 *   <tr><th>Condition</th><th>Action</th></tr>
 *   <tr><td>Risk score ≥ 85 for a single message</td><td>Fire immediately</td></tr>
 *   <tr><td>Risk score ≥ 65 for 3 consecutive USER messages</td><td>Fire escalation</td></tr>
 *   <tr><td>Escalation already active for this session</td><td>Suppress (deduplicate)</td></tr>
 * </table>
 *
 * <p>When escalation fires:</p>
 * <ol>
 *   <li>Persist an {@link EscalationLog} entry</li>
 *   <li>Call {@link NotificationService#notify} (therapist queue + outbox)</li>
 *   <li>Broadcast {@code crisis_escalation} WS event to the session</li>
 * </ol>
 *
 * <p>End-to-end SLA: escalation must complete within 3 seconds of the
 * triggering message.</p>
 */
@Service
public class EscalationEngine {

    private static final Logger logger = LoggerFactory.getLogger(EscalationEngine.class);

    /** Threshold for immediate single-message escalation. */
    private static final int SINGLE_MESSAGE_THRESHOLD = 85;

    /** Threshold for consecutive-message escalation. */
    private static final int CONSECUTIVE_THRESHOLD = 65;

    /** Number of consecutive high-risk messages required to trigger escalation. */
    private static final int CONSECUTIVE_COUNT = 3;

    private final EscalationLogRepository escalationLogRepository;
    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Construct the escalation engine.
     *
     * @param escalationLogRepository repository for escalation log persistence
     * @param messageRepository       repository for message history queries
     * @param sessionRepository       repository for session lookups
     * @param notificationService     service for therapist notifications
     * @param messagingTemplate       WebSocket messaging for crisis events
     */
    public EscalationEngine(EscalationLogRepository escalationLogRepository,
                             MessageRepository messageRepository,
                             SessionRepository sessionRepository,
                             NotificationService notificationService,
                             SimpMessagingTemplate messagingTemplate) {
        this.escalationLogRepository = escalationLogRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Evaluate whether an escalation should fire for the given session and risk score.
     *
     * <p>This is the primary entry point, called at the end of the Step 8
     * risk scoring pipeline. The method checks both trigger rules and handles
     * deduplication before firing.</p>
     *
     * @param sessionId    the session ID to evaluate
     * @param newRiskScore the risk score from the latest message
     * @return an {@link EscalationResult} indicating whether escalation was fired
     */
    @Transactional
    public EscalationResult evaluate(Long sessionId, int newRiskScore) {
        long startTime = System.currentTimeMillis();

        // 1. Deduplication check — skip if active escalation already exists
        if (escalationLogRepository.existsBySessionIdAndIsActiveTrue(sessionId)) {
            logger.info("Escalation suppressed for session {}: active escalation already exists",
                    sessionId);
            return EscalationResult.suppressed(sessionId);
        }

        // 2. Check single-message immediate trigger (score ≥ 85)
        if (newRiskScore >= SINGLE_MESSAGE_THRESHOLD) {
            logger.warn("IMMEDIATE ESCALATION: session={}, score={} (threshold={})",
                    sessionId, newRiskScore, SINGLE_MESSAGE_THRESHOLD);
            return fireEscalation(sessionId, newRiskScore, "single_85", startTime);
        }

        // 3. Check consecutive-3 trigger (3 consecutive USER messages ≥ 65)
        if (newRiskScore >= CONSECUTIVE_THRESHOLD && checkConsecutiveHighRisk(sessionId)) {
            logger.warn("CONSECUTIVE ESCALATION: session={}, score={} (3 consecutive ≥ {})",
                    sessionId, newRiskScore, CONSECUTIVE_THRESHOLD);
            return fireEscalation(sessionId, newRiskScore, "consecutive_3", startTime);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.debug("No escalation for session {}, score={}, elapsed={}ms",
                sessionId, newRiskScore, elapsed);

        return EscalationResult.noEscalation(sessionId);
    }

    /**
     * Check if the last 3 USER messages in the session all have risk_score ≥ 65.
     *
     * <p>Queries the most recent 3 USER messages (ordered by creation time
     * descending) and checks that all have a risk_score at or above the
     * consecutive threshold. The current message's risk_score should already
     * be persisted before this check.</p>
     *
     * @param sessionId the session to check
     * @return true if the last 3 consecutive USER messages all scored ≥ 65
     */
    private boolean checkConsecutiveHighRisk(Long sessionId) {
        List<Message> recentMessages = messageRepository
                .findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                        sessionId, "USER", PageRequest.of(0, CONSECUTIVE_COUNT));

        if (recentMessages.size() < CONSECUTIVE_COUNT) {
            return false;
        }

        return recentMessages.stream()
                .allMatch(msg -> msg.getRiskScore() != null
                        && msg.getRiskScore() >= CONSECUTIVE_THRESHOLD);
    }

    /**
     * Execute the full escalation sequence: persist → notify → broadcast.
     *
     * @param sessionId    the session ID
     * @param riskScore    the triggering risk score
     * @param triggerRule  the rule that was breached ("single_85" or "consecutive_3")
     * @param startTime    the start timestamp for latency tracking
     * @return an {@link EscalationResult} with fired=true
     */
    private EscalationResult fireEscalation(Long sessionId, int riskScore,
                                             String triggerRule, long startTime) {
        // 1. Load session entity
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // 2. Persist escalation log
        EscalationLog escalationLog = new EscalationLog(
                session,
                session.getUser(),
                triggerRule,
                riskScore
        );
        escalationLog = escalationLogRepository.save(escalationLog);
        logger.info("Escalation log persisted: id={}, session={}, rule={}, score={}",
                escalationLog.getId(), sessionId, triggerRule, riskScore);

        // 3. Notify (therapist queue + outbox)
        notificationService.notify(escalationLog);

        // 4. Broadcast crisis_escalation WS event
        broadcastCrisisEvent(sessionId);

        // 5. Log latency
        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Escalation completed in {}ms for session {} (SLA: <3000ms)", elapsed, sessionId);

        if (elapsed > 3000) {
            logger.error("ESCALATION SLA BREACH: {}ms exceeds 3-second target for session {}",
                    elapsed, sessionId);
        }

        return EscalationResult.fired(triggerRule, sessionId);
    }

    /**
     * Broadcast a {@code crisis_escalation} event to the session's WebSocket topic.
     *
     * @param sessionId the session whose subscribers should receive the event
     */
    private void broadcastCrisisEvent(Long sessionId) {
        try {
            CrisisEscalationEvent event = CrisisEscalationEvent.withDefaults(sessionId);

            messagingTemplate.convertAndSend(
                    "/topic/session." + sessionId + ".crisis",
                    event
            );

            logger.info("Crisis escalation event broadcast to /topic/session.{}.crisis", sessionId);
        } catch (Exception e) {
            logger.error("Failed to broadcast crisis_escalation event for session {}: {}",
                    sessionId, e.getMessage());
        }
    }
}
