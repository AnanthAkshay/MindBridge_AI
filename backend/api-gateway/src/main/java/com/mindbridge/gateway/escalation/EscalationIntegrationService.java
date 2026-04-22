package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.Message;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.gateway.risk.WeightedRiskAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Integration bridge between the Step 8 risk scoring pipeline
 * and the Step 9 escalation engine.
 *
 * <p>This service is called after the risk pipeline completes.
 * It stamps the risk score on the latest message entity and then
 * delegates to the {@link EscalationEngine} for evaluation.</p>
 *
 * <p>By encapsulating this in a separate service, we avoid modifying
 * the Step 8 {@code RiskScoringPipeline} source.</p>
 */
@Service
public class EscalationIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(EscalationIntegrationService.class);

    private final EscalationEngine escalationEngine;
    private final MessageRepository messageRepository;

    /**
     * Construct the integration service.
     *
     * @param escalationEngine  the escalation engine to delegate to
     * @param messageRepository repository for stamping risk scores on messages
     */
    public EscalationIntegrationService(EscalationEngine escalationEngine,
                                         MessageRepository messageRepository) {
        this.escalationEngine = escalationEngine;
        this.messageRepository = messageRepository;
    }

    /**
     * Process a risk result by stamping the message and evaluating for escalation.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Find the most recent USER message in the session and set its risk_score</li>
     *   <li>Delegate to {@link EscalationEngine#evaluate} with the score</li>
     * </ol>
     *
     * @param sessionId the session ID
     * @param result    the risk result from the Step 8 pipeline
     * @return the escalation evaluation result
     */
    @Transactional
    public EscalationResult processRiskResult(Long sessionId,
                                               WeightedRiskAggregator.RiskResult result) {
        // 1. Stamp risk score on the latest USER message
        stampLatestMessageRiskScore(sessionId, result.score());

        // 2. Evaluate escalation
        return escalationEngine.evaluate(sessionId, result.score());
    }

    /**
     * Find the most recent USER message in the session and update its risk_score.
     *
     * @param sessionId the session ID
     * @param riskScore the risk score to stamp
     */
    private void stampLatestMessageRiskScore(Long sessionId, int riskScore) {
        try {
            List<Message> recentMessages = messageRepository
                    .findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                            sessionId, "USER",
                            org.springframework.data.domain.PageRequest.of(0, 1));

            if (!recentMessages.isEmpty()) {
                Message latest = recentMessages.get(0);
                latest.setRiskScore(riskScore);
                messageRepository.save(latest);
                logger.debug("Stamped risk_score={} on message {} in session {}",
                        riskScore, latest.getId(), sessionId);
            }
        } catch (Exception e) {
            logger.error("Failed to stamp risk score on message for session {}: {}",
                    sessionId, e.getMessage());
        }
    }
}
