package com.mindbridge.gateway.risk;

import com.mindbridge.core.entity.RiskLevel;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.gateway.chat.NlpServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Orchestrator for the multi-signal real-time risk scoring pipeline.
 *
 * <p>Every incoming chat message is scored 0–100 and classified as
 * LOW / MODERATE / HIGH by combining three sub-scores:</p>
 * <ol>
 *   <li>{@link KeywordClassifier} — crisis vocabulary scan (0–40)</li>
 *   <li>{@link TonalEmotionScorer} — NLP emotion mapping (0–40)</li>
 *   <li>{@link SessionBaselineAdjuster} — cross-session history modifier (0–20)</li>
 * </ol>
 *
 * <p>After scoring, the result is persisted to the database and broadcast
 * to the session's WebSocket topic as a {@code risk_update} event.
 * End-to-end latency target: &lt; 200 ms.</p>
 */
@Service
public class RiskScoringPipeline {

    private static final Logger logger = LoggerFactory.getLogger(RiskScoringPipeline.class);

    private final KeywordClassifier keywordClassifier;
    private final TonalEmotionScorer tonalEmotionScorer;
    private final SessionBaselineAdjuster sessionBaselineAdjuster;
    private final WeightedRiskAggregator weightedRiskAggregator;
    private final SessionRepository sessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Construct the pipeline with all required components.
     *
     * @param keywordClassifier       crisis keyword scanner
     * @param tonalEmotionScorer      NLP emotion scorer
     * @param sessionBaselineAdjuster cross-session baseline modifier
     * @param weightedRiskAggregator  final score aggregator
     * @param sessionRepository       database access for session persistence
     * @param messagingTemplate       WebSocket messaging for risk_update broadcasts
     */
    public RiskScoringPipeline(
            KeywordClassifier keywordClassifier,
            TonalEmotionScorer tonalEmotionScorer,
            SessionBaselineAdjuster sessionBaselineAdjuster,
            WeightedRiskAggregator weightedRiskAggregator,
            SessionRepository sessionRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.keywordClassifier = keywordClassifier;
        this.tonalEmotionScorer = tonalEmotionScorer;
        this.sessionBaselineAdjuster = sessionBaselineAdjuster;
        this.weightedRiskAggregator = weightedRiskAggregator;
        this.sessionRepository = sessionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Score an incoming message and broadcast the result.
     *
     * <p>This is the primary entry point called from the message handler.
     * It executes the full pipeline:</p>
     * <ol>
     *   <li>Run keyword classification on raw message text</li>
     *   <li>Run tonal scoring on existing NLP output (no duplicate NLP call)</li>
     *   <li>Compute session baseline modifier from history</li>
     *   <li>Aggregate into final score and risk level</li>
     *   <li>Persist to database</li>
     *   <li>Broadcast risk_update WS event</li>
     * </ol>
     *
     * @param message     the raw plaintext message content
     * @param sessionId   the session ID to score against
     * @param nlpResponse the pre-computed NLP result (from Step 4, must NOT be re-run)
     * @return the aggregated {@link WeightedRiskAggregator.RiskResult}
     */
    @Transactional
    public WeightedRiskAggregator.RiskResult score(String message, Long sessionId,
                                                    NlpServiceClient.NlpResponse nlpResponse) {
        long startTime = System.currentTimeMillis();

        // 1. Keyword classification (0–40)
        double keywordScore = keywordClassifier.score(message);

        // 2. Tonal emotion scoring (0–40) — consumes existing NLP output
        double tonalScore = tonalEmotionScorer.score(nlpResponse);

        // 3. Session baseline adjustment (0–20)
        double baselineScore = sessionBaselineAdjuster.adjust(sessionId);

        // 4. Aggregate
        WeightedRiskAggregator.RiskResult result = weightedRiskAggregator.aggregate(
                keywordScore, tonalScore, baselineScore);

        // 5. Persist to database
        persistRiskScore(sessionId, result);

        // 6. Broadcast WebSocket event
        broadcastRiskUpdate(sessionId, result);

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("RiskScoringPipeline: sessionId={}, score={}, level={}, latency={}ms",
                sessionId, result.score(), result.level(), elapsed);

        if (elapsed > 200) {
            logger.warn("Risk scoring latency exceeded 200ms target: {}ms for session {}",
                    elapsed, sessionId);
        }

        return result;
    }

    /**
     * Persist the risk score and level to the session record.
     *
     * @param sessionId the session to update
     * @param result    the computed risk result
     */
    private void persistRiskScore(Long sessionId, WeightedRiskAggregator.RiskResult result) {
        try {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                session.setRiskScore(result.score());
                session.setRiskLevel(result.level());
                session.setRiskUpdatedAt(Instant.now());
                sessionRepository.save(session);
            }
        } catch (Exception e) {
            logger.error("Failed to persist risk score for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Broadcast a {@code risk_update} event to the session's WebSocket room.
     *
     * <p>Event format:</p>
     * <pre>{@code
     * {
     *   "type": "risk_update",
     *   "score": 72,
     *   "level": "HIGH",
     *   "sessionId": "42"
     * }
     * }</pre>
     *
     * @param sessionId the session whose subscribers should receive the event
     * @param result    the computed risk result
     */
    private void broadcastRiskUpdate(Long sessionId, WeightedRiskAggregator.RiskResult result) {
        try {
            RiskUpdateEvent event = new RiskUpdateEvent(
                    "risk_update",
                    result.score(),
                    result.level().name(),
                    String.valueOf(sessionId)
            );

            messagingTemplate.convertAndSend(
                    "/topic/session." + sessionId + ".risk",
                    event
            );

            logger.debug("Broadcast risk_update to /topic/session.{}.risk: score={}, level={}",
                    sessionId, result.score(), result.level());
        } catch (Exception e) {
            logger.error("Failed to broadcast risk_update for session {}: {}",
                    sessionId, e.getMessage());
        }
    }
}
