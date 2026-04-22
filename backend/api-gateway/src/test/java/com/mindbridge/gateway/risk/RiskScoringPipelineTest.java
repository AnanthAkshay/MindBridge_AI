package com.mindbridge.gateway.risk;

import com.mindbridge.core.entity.RiskLevel;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.gateway.chat.NlpServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-level tests for the full {@link RiskScoringPipeline}.
 *
 * <p>Validates all four acceptance criteria from Step 8 requirements:</p>
 * <ol>
 *   <li>Suicidal keyword → final score ≥ 80</li>
 *   <li>Purely positive message → score decreases vs neutral baseline</li>
 *   <li>3 consecutive HIGH-risk sessions → baseline modifier is elevated</li>
 *   <li>risk_update WS event arrives in &lt; 200 ms</li>
 * </ol>
 */
class RiskScoringPipelineTest {

    private SessionRepository sessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private RiskScoringPipeline pipeline;

    @BeforeEach
    void setUp() {
        sessionRepository = Mockito.mock(SessionRepository.class);
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        KeywordClassifier keywordClassifier = new KeywordClassifier();
        TonalEmotionScorer tonalEmotionScorer = new TonalEmotionScorer();
        SessionBaselineAdjuster baselineAdjuster = new SessionBaselineAdjuster(sessionRepository);
        WeightedRiskAggregator aggregator = new WeightedRiskAggregator(0.4, 0.4, 0.2);

        pipeline = new RiskScoringPipeline(
                keywordClassifier, tonalEmotionScorer, baselineAdjuster,
                aggregator, sessionRepository, messagingTemplate);

        // Default session mock
        User user = new User();
        user.setId(1L);
        Session session = new Session();
        session.setId(1L);
        session.setUser(user);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * Acceptance Test 1: Message containing a suicidal keyword → final score ≥ 80.
     */
    @Test
    @DisplayName("AT1: Suicidal keyword message → score ≥ 80")
    void suicidalKeyword_scoreAbove80() {
        NlpServiceClient.NlpResponse nlp =
                new NlpServiceClient.NlpResponse("sadness", 0.9, -0.9, -0.6);

        WeightedRiskAggregator.RiskResult result = pipeline.score(
                "I want to kill myself and end my life, there's no hope left",
                1L, nlp);

        assertTrue(result.score() >= 80,
                "Suicidal keyword message should score ≥ 80, got: " + result.score());
        assertEquals(RiskLevel.HIGH, result.level());
    }

    /**
     * Acceptance Test 2: Purely positive message → score decreases vs neutral baseline.
     */
    @Test
    @DisplayName("AT2: Positive message → score lower than neutral")
    void positiveMessage_lowerThanNeutral() {
        NlpServiceClient.NlpResponse positiveNlp =
                new NlpServiceClient.NlpResponse("joy", 0.9, 0.9, 0.5);
        NlpServiceClient.NlpResponse neutralNlp =
                new NlpServiceClient.NlpResponse("neutral", 0.65, 0.0, 0.0);

        WeightedRiskAggregator.RiskResult positiveResult = pipeline.score(
                "I am feeling wonderful and so grateful for my life", 1L, positiveNlp);
        WeightedRiskAggregator.RiskResult neutralResult = pipeline.score(
                "The weather is okay today", 1L, neutralNlp);

        assertTrue(positiveResult.score() <= neutralResult.score(),
                "Positive (" + positiveResult.score() + ") should be ≤ neutral (" + neutralResult.score() + ")");
    }

    /**
     * Acceptance Test 3: 3 consecutive HIGH-risk sessions → baseline modifier is elevated.
     */
    @Test
    @DisplayName("AT3: 3 consecutive HIGH sessions → baseline modifier elevates final score")
    void threeHighSessions_elevatedBaseline() {
        Instant now = Instant.now();
        User user = new User();
        user.setId(1L);

        Session current = new Session();
        current.setId(4L);
        current.setUser(user);

        Session s1 = new Session();
        s1.setId(1L);
        s1.setUser(user);
        s1.setRiskLevel(RiskLevel.HIGH);
        s1.setRiskScore(80);
        s1.setRiskUpdatedAt(now.minus(1, ChronoUnit.HOURS));
        s1.setCreatedAt(now.minus(1, ChronoUnit.HOURS));

        Session s2 = new Session();
        s2.setId(2L);
        s2.setUser(user);
        s2.setRiskLevel(RiskLevel.HIGH);
        s2.setRiskScore(85);
        s2.setRiskUpdatedAt(now.minus(2, ChronoUnit.HOURS));
        s2.setCreatedAt(now.minus(2, ChronoUnit.HOURS));

        Session s3 = new Session();
        s3.setId(3L);
        s3.setUser(user);
        s3.setRiskLevel(RiskLevel.HIGH);
        s3.setRiskScore(75);
        s3.setRiskUpdatedAt(now.minus(3, ChronoUnit.HOURS));
        s3.setCreatedAt(now.minus(3, ChronoUnit.HOURS));

        when(sessionRepository.findById(4L)).thenReturn(Optional.of(current));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(current, s1, s2, s3));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        NlpServiceClient.NlpResponse neutralNlp =
                new NlpServiceClient.NlpResponse("neutral", 0.65, 0.0, 0.0);

        // Score with history (elevated baseline)
        WeightedRiskAggregator.RiskResult withHistory = pipeline.score(
                "I'm feeling okay", 4L, neutralNlp);

        // Score without history (reset mock)
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(current));
        WeightedRiskAggregator.RiskResult withoutHistory = pipeline.score(
                "I'm feeling okay", 4L, neutralNlp);

        assertTrue(withHistory.score() > withoutHistory.score(),
                "Score with 3 HIGH sessions (" + withHistory.score() +
                ") should be > score without history (" + withoutHistory.score() + ")");
    }

    /**
     * Acceptance Test 4: risk_update WS event is sent and pipeline completes in < 200 ms.
     */
    @Test
    @DisplayName("AT4: risk_update WS event sent in < 200ms")
    void riskUpdateEvent_sentUnder200ms() {
        NlpServiceClient.NlpResponse nlp =
                new NlpServiceClient.NlpResponse("sadness", 0.85, -0.8, -0.4);

        long start = System.currentTimeMillis();
        WeightedRiskAggregator.RiskResult result = pipeline.score(
                "I feel so hopeless and alone", 1L, nlp);
        long elapsed = System.currentTimeMillis() - start;

        // Verify WS event was broadcast
        ArgumentCaptor<RiskUpdateEvent> captor = ArgumentCaptor.forClass(RiskUpdateEvent.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/session.1.risk"),
                captor.capture()
        );

        RiskUpdateEvent event = captor.getValue();
        assertEquals("risk_update", event.type());
        assertEquals(result.score(), event.score());
        assertEquals(result.level().name(), event.level());
        assertEquals("1", event.sessionId());

        // Verify latency
        assertTrue(elapsed < 200, "Pipeline latency must be < 200ms, was: " + elapsed + "ms");
    }

    @Test
    @DisplayName("Risk score is persisted to session entity")
    void riskScore_persistedToSession() {
        NlpServiceClient.NlpResponse nlp =
                new NlpServiceClient.NlpResponse("fear", 0.8, -0.7, 0.6);

        pipeline.score("I'm scared and panicking", 1L, nlp);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(sessionCaptor.capture());

        Session savedSession = sessionCaptor.getValue();
        assertNotNull(savedSession.getRiskScore(), "Risk score should be set");
        assertNotNull(savedSession.getRiskLevel(), "Risk level should be set");
        assertNotNull(savedSession.getRiskUpdatedAt(), "Risk updated timestamp should be set");
        assertTrue(savedSession.getRiskScore() >= 0 && savedSession.getRiskScore() <= 100,
                "Risk score should be 0-100");
    }
}
