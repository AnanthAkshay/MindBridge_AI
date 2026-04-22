package com.mindbridge.gateway.risk;

import com.mindbridge.core.entity.RiskLevel;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionBaselineAdjuster}.
 *
 * <p>Uses Mockito to simulate session history scenarios.</p>
 */
class SessionBaselineAdjusterTest {

    private SessionRepository sessionRepository;
    private SessionBaselineAdjuster adjuster;

    @BeforeEach
    void setUp() {
        sessionRepository = Mockito.mock(SessionRepository.class);
        adjuster = new SessionBaselineAdjuster(sessionRepository);
    }

    /**
     * Create a test session with the given risk attributes.
     */
    private Session createSession(Long id, Long userId, RiskLevel riskLevel, Instant riskUpdatedAt) {
        User user = new User();
        user.setId(userId);

        Session session = new Session();
        session.setId(id);
        session.setUser(user);
        session.setRiskLevel(riskLevel);
        session.setRiskScore(riskLevel == RiskLevel.HIGH ? 80 : riskLevel == RiskLevel.MODERATE ? 50 : 10);
        session.setRiskUpdatedAt(riskUpdatedAt);
        session.setCreatedAt(riskUpdatedAt != null ? riskUpdatedAt : Instant.now());
        return session;
    }

    @Test
    @DisplayName("Session not found → modifier is 0")
    void sessionNotFound_returnsZero() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());
        assertEquals(0.0, adjuster.adjust(999L));
    }

    @Test
    @DisplayName("No previous sessions → modifier is 0")
    void noPreviousSessions_returnsZero() {
        Session current = createSession(1L, 100L, RiskLevel.LOW, Instant.now());
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(current));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(current)); // Only the current session exists

        assertEquals(0.0, adjuster.adjust(1L));
    }

    @Test
    @DisplayName("All previous sessions LOW → modifier is 0")
    void allLowSessions_returnsZero() {
        Instant now = Instant.now();
        Session current = createSession(4L, 100L, RiskLevel.LOW, now);
        Session s1 = createSession(1L, 100L, RiskLevel.LOW, now.minus(1, ChronoUnit.HOURS));
        Session s2 = createSession(2L, 100L, RiskLevel.LOW, now.minus(2, ChronoUnit.HOURS));
        Session s3 = createSession(3L, 100L, RiskLevel.LOW, now.minus(3, ChronoUnit.HOURS));

        when(sessionRepository.findById(4L)).thenReturn(Optional.of(current));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(current, s3, s2, s1));

        assertEquals(0.0, adjuster.adjust(4L));
    }

    @Test
    @DisplayName("All 3 previous sessions HIGH → baseline modifier is elevated (≈20)")
    void allHighSessions_elevatedModifier() {
        Instant now = Instant.now();
        Session current = createSession(4L, 100L, RiskLevel.LOW, now);
        Session s1 = createSession(1L, 100L, RiskLevel.HIGH, now.minus(1, ChronoUnit.HOURS));
        Session s2 = createSession(2L, 100L, RiskLevel.HIGH, now.minus(2, ChronoUnit.HOURS));
        Session s3 = createSession(3L, 100L, RiskLevel.HIGH, now.minus(3, ChronoUnit.HOURS));

        when(sessionRepository.findById(4L)).thenReturn(Optional.of(current));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(current, s1, s2, s3));

        double modifier = adjuster.adjust(4L);
        assertTrue(modifier > 15.0, "3 consecutive HIGH sessions should produce modifier near 20, got: " + modifier);
        assertTrue(modifier <= 20.0, "Modifier must not exceed 20");
    }

    @Test
    @DisplayName("Mixed risk sessions → proportional modifier")
    void mixedRiskSessions_proportionalModifier() {
        Instant now = Instant.now();
        Session current = createSession(4L, 100L, RiskLevel.LOW, now);
        Session s1 = createSession(1L, 100L, RiskLevel.HIGH, now.minus(1, ChronoUnit.HOURS));
        Session s2 = createSession(2L, 100L, RiskLevel.LOW, now.minus(2, ChronoUnit.HOURS));
        Session s3 = createSession(3L, 100L, RiskLevel.MODERATE, now.minus(3, ChronoUnit.HOURS));

        when(sessionRepository.findById(4L)).thenReturn(Optional.of(current));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(current, s1, s2, s3));

        double modifier = adjuster.adjust(4L);
        assertTrue(modifier > 0, "Mixed sessions with some elevated should produce positive modifier");
        assertTrue(modifier < 20.0, "Not all sessions elevated, modifier should be less than max");
    }

    @Test
    @DisplayName("Decay after 24+ hours reduces modifier")
    void decayAfter24Hours_reducedModifier() {
        Instant old = Instant.now().minus(48, ChronoUnit.HOURS); // 48 hours ago
        Session current = createSession(4L, 100L, RiskLevel.LOW, Instant.now());
        Session s1 = createSession(1L, 100L, RiskLevel.HIGH, old);
        Session s2 = createSession(2L, 100L, RiskLevel.HIGH, old.minus(1, ChronoUnit.HOURS));
        Session s3 = createSession(3L, 100L, RiskLevel.HIGH, old.minus(2, ChronoUnit.HOURS));

        when(sessionRepository.findById(4L)).thenReturn(Optional.of(current));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(current, s1, s2, s3));

        double modifier = adjuster.adjust(4L);
        // 48 hours = 2 decay periods → modifier should be 20 × 0.25 = 5
        assertTrue(modifier < 10.0, "Modifier should be significantly decayed after 48h, got: " + modifier);
        assertTrue(modifier > 0.0, "Modifier should still be positive");
    }
}
