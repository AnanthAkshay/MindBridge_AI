package com.mindbridge.gateway.risk;

import com.mindbridge.core.entity.RiskLevel;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Session baseline adjuster for cross-session risk escalation.
 *
 * <p>Queries the last 3 sessions for a user. If all had elevated risk
 * (MODERATE or HIGH), a baseline modifier of +0 to +20 is applied.
 * The modifier decays over time: it is halved for every 24 hours since
 * the last high-risk message.</p>
 *
 * <p>Sub-score range: [0, 20]</p>
 */
@Component
public class SessionBaselineAdjuster {

    private static final Logger logger = LoggerFactory.getLogger(SessionBaselineAdjuster.class);

    /** Maximum baseline modifier this adjuster can add. */
    private static final double MAX_BASELINE = 20.0;

    /** Number of recent sessions to inspect for escalation history. */
    private static final int LOOKBACK_COUNT = 3;

    /** Duration after which the baseline modifier is halved. */
    private static final Duration DECAY_INTERVAL = Duration.ofHours(24);

    private final SessionRepository sessionRepository;

    /**
     * Construct the adjuster.
     *
     * @param sessionRepository repository for querying session history
     */
    public SessionBaselineAdjuster(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Calculate the baseline modifier for the given session.
     *
     * <p>Logic:</p>
     * <ol>
     *   <li>Fetch the user's last {@value #LOOKBACK_COUNT} sessions (excluding current).</li>
     *   <li>Count how many had risk level MODERATE or HIGH.</li>
     *   <li>Base modifier = (elevatedCount / LOOKBACK_COUNT) × MAX_BASELINE</li>
     *   <li>Apply time decay: halve the modifier for every 24 h since the most recent
     *       high-risk timestamp.</li>
     * </ol>
     *
     * @param sessionId the current session ID
     * @return a baseline modifier in the range [0, 20]
     */
    public double adjust(Long sessionId) {
        Session currentSession;
        try {
            currentSession = sessionRepository.findById(sessionId).orElse(null);
        } catch (Exception e) {
            logger.warn("Failed to load session {}: {}", sessionId, e.getMessage());
            return 0.0;
        }

        if (currentSession == null || currentSession.getUser() == null) {
            return 0.0;
        }

        Long userId = currentSession.getUser().getId();

        // Fetch last N+1 sessions (to exclude the current one)
        List<Session> recentSessions;
        try {
            recentSessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, PageRequest.of(0, LOOKBACK_COUNT + 1));
        } catch (Exception e) {
            logger.warn("Failed to query session history for user {}: {}", userId, e.getMessage());
            return 0.0;
        }

        // Filter out the current session
        List<Session> previousSessions = recentSessions.stream()
                .filter(s -> !s.getId().equals(sessionId))
                .limit(LOOKBACK_COUNT)
                .toList();

        if (previousSessions.isEmpty()) {
            return 0.0;
        }

        // Count how many of the last N sessions had elevated risk
        long elevatedCount = previousSessions.stream()
                .filter(s -> s.getRiskLevel() == RiskLevel.HIGH || s.getRiskLevel() == RiskLevel.MODERATE)
                .count();

        if (elevatedCount == 0) {
            return 0.0;
        }

        // Base modifier proportional to elevated session ratio
        double baseModifier = ((double) elevatedCount / LOOKBACK_COUNT) * MAX_BASELINE;

        // Find the most recent risk-updated timestamp among elevated sessions
        Instant mostRecentRisk = previousSessions.stream()
                .filter(s -> s.getRiskLevel() == RiskLevel.HIGH || s.getRiskLevel() == RiskLevel.MODERATE)
                .map(s -> s.getRiskUpdatedAt() != null ? s.getRiskUpdatedAt() : s.getCreatedAt())
                .max(Instant::compareTo)
                .orElse(Instant.now());

        // Time decay: halve modifier for every 24 h since last high-risk event
        Duration elapsed = Duration.between(mostRecentRisk, Instant.now());
        long decayPeriods = elapsed.toHours() / DECAY_INTERVAL.toHours();
        double decayFactor = Math.pow(0.5, decayPeriods);

        double adjustedModifier = baseModifier * decayFactor;
        double clamped = Math.max(0.0, Math.min(adjustedModifier, MAX_BASELINE));

        logger.debug("SessionBaselineAdjuster: userId={}, elevatedCount={}/{}, baseModifier={}, " +
                     "decayPeriods={}, decayFactor={}, clamped={}",
                userId, elevatedCount, LOOKBACK_COUNT, baseModifier,
                decayPeriods, decayFactor, clamped);

        return clamped;
    }
}
