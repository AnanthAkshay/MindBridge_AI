package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.Message;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EscalationEngine}.
 *
 * <p>Validates all escalation trigger rules, deduplication logic,
 * and the acceptance criteria from Step 9.</p>
 */
class EscalationEngineTest {

    private EscalationLogRepository escalationLogRepository;
    private MessageRepository messageRepository;
    private SessionRepository sessionRepository;
    private NotificationService notificationService;
    private SimpMessagingTemplate messagingTemplate;
    private EscalationEngine engine;

    private Session testSession;
    private User testUser;

    @BeforeEach
    void setUp() {
        escalationLogRepository = Mockito.mock(EscalationLogRepository.class);
        messageRepository = Mockito.mock(MessageRepository.class);
        sessionRepository = Mockito.mock(SessionRepository.class);
        notificationService = Mockito.mock(NotificationService.class);
        messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        engine = new EscalationEngine(
                escalationLogRepository, messageRepository,
                sessionRepository, notificationService, messagingTemplate);

        // Set up test session and user
        testUser = new User();
        testUser.setId(1L);

        testSession = new Session();
        testSession.setId(1L);
        testSession.setUser(testUser);

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(escalationLogRepository.existsBySessionIdAndIsActiveTrue(1L)).thenReturn(false);
        when(escalationLogRepository.save(any(EscalationLog.class)))
                .thenAnswer(invocation -> {
                    EscalationLog log = invocation.getArgument(0);
                    log.setId(100L);
                    return log;
                });
    }

    /**
     * Helper to create a mock Message with a risk score.
     */
    private Message createMessage(Long id, int riskScore) {
        Message msg = new Message();
        msg.setId(id);
        msg.setSession(testSession);
        msg.setSenderType("USER");
        msg.setRiskScore(riskScore);
        msg.setCreatedAt(Instant.now());
        return msg;
    }

    // ==================== Acceptance Test 1 ====================

    /**
     * AT1: 3 consecutive messages each scoring ≥ 65 → escalation fires once.
     */
    @Test
    @DisplayName("AT1: 3 consecutive messages ≥ 65 → escalation fires")
    void threeConsecutiveMessagesAbove65_fires() {
        // Simulate 3 consecutive messages all scoring ≥ 65
        List<Message> recentMessages = List.of(
                createMessage(3L, 70),
                createMessage(2L, 68),
                createMessage(1L, 65)
        );
        when(messageRepository.findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                eq(1L), eq("USER"), any(Pageable.class)))
                .thenReturn(recentMessages);

        EscalationResult result = engine.evaluate(1L, 70);

        assertTrue(result.fired(), "Escalation should fire for 3 consecutive ≥ 65");
        assertEquals("consecutive_3", result.reason());

        // Verify escalation log was persisted
        verify(escalationLogRepository).save(any(EscalationLog.class));
        // Verify notification was sent
        verify(notificationService).notify(any(EscalationLog.class));
        // Verify WS event was broadcast
        verify(messagingTemplate).convertAndSend(
                eq("/topic/session.1.crisis"),
                any(CrisisEscalationEvent.class));
    }

    // ==================== Acceptance Test 2 ====================

    /**
     * AT2: Single message scoring ≥ 85 → immediate escalation.
     */
    @Test
    @DisplayName("AT2: Single message ≥ 85 → immediate escalation")
    void singleMessageAbove85_immediateEscalation() {
        EscalationResult result = engine.evaluate(1L, 90);

        assertTrue(result.fired(), "Escalation should fire immediately for score ≥ 85");
        assertEquals("single_85", result.reason());
        verify(escalationLogRepository).save(any(EscalationLog.class));
        verify(notificationService).notify(any(EscalationLog.class));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/session.1.crisis"),
                any(CrisisEscalationEvent.class));
    }

    // ==================== Acceptance Test 3 ====================

    /**
     * AT3: With active escalation, another ≥ 85 message → NO second escalation.
     */
    @Test
    @DisplayName("AT3: Active escalation → suppress duplicate")
    void activeEscalation_suppressesDuplicate() {
        // Simulate an existing active escalation
        when(escalationLogRepository.existsBySessionIdAndIsActiveTrue(1L)).thenReturn(true);

        EscalationResult result = engine.evaluate(1L, 95);

        assertFalse(result.fired(), "Should NOT fire when active escalation exists");
        assertTrue(result.reason().contains("suppressed"),
                "Reason should indicate suppression");

        // Verify NO new escalation was persisted or notified
        verify(escalationLogRepository, never()).save(any(EscalationLog.class));
        verify(notificationService, never()).notify(any(EscalationLog.class));
        verify(messagingTemplate, never()).convertAndSend(
                anyString(), any(CrisisEscalationEvent.class));
    }

    // ==================== Acceptance Test 4 ====================

    /**
     * AT4: Crisis escalation event is sent within 3 seconds.
     */
    @Test
    @DisplayName("AT4: Escalation completes within 3-second SLA")
    void escalation_completesWithin3Seconds() {
        long start = System.currentTimeMillis();

        EscalationResult result = engine.evaluate(1L, 90);

        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.fired());
        assertTrue(elapsed < 3000,
                "Escalation must complete within 3 seconds, took: " + elapsed + "ms");

        // Verify WS event was broadcast
        verify(messagingTemplate).convertAndSend(
                eq("/topic/session.1.crisis"),
                any(CrisisEscalationEvent.class));
    }

    // ==================== Additional Tests ====================

    @Test
    @DisplayName("Score below thresholds → no escalation")
    void scoreBelowThresholds_noEscalation() {
        EscalationResult result = engine.evaluate(1L, 50);

        assertFalse(result.fired());
        verify(escalationLogRepository, never()).save(any(EscalationLog.class));
    }

    @Test
    @DisplayName("Score ≥ 65 but only 2 consecutive → no escalation")
    void twoConsecutiveOnly_noEscalation() {
        // Only 2 messages above threshold (not enough)
        List<Message> recentMessages = List.of(
                createMessage(2L, 70),
                createMessage(1L, 68)
        );
        when(messageRepository.findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                eq(1L), eq("USER"), any(Pageable.class)))
                .thenReturn(recentMessages);

        EscalationResult result = engine.evaluate(1L, 66);

        assertFalse(result.fired(),
                "Should not fire with only 2 consecutive messages above threshold");
    }

    @Test
    @DisplayName("3 messages but one below 65 → no escalation")
    void threeMessagesOneBelowThreshold_noEscalation() {
        List<Message> recentMessages = List.of(
                createMessage(3L, 70),
                createMessage(2L, 40),  // Below threshold
                createMessage(1L, 68)
        );
        when(messageRepository.findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                eq(1L), eq("USER"), any(Pageable.class)))
                .thenReturn(recentMessages);

        EscalationResult result = engine.evaluate(1L, 66);

        assertFalse(result.fired(),
                "Should not fire when one of 3 messages is below threshold");
    }

    @Test
    @DisplayName("Score exactly 85 → immediate escalation fires")
    void exactlyThreshold85_fires() {
        EscalationResult result = engine.evaluate(1L, 85);
        assertTrue(result.fired());
        assertEquals("single_85", result.reason());
    }

    @Test
    @DisplayName("Score exactly 84 → no immediate escalation")
    void justBelowThreshold85_noImmediate() {
        // No consecutive messages either
        when(messageRepository.findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                eq(1L), eq("USER"), any(Pageable.class)))
                .thenReturn(List.of());

        EscalationResult result = engine.evaluate(1L, 84);
        assertFalse(result.fired());
    }

    @Test
    @DisplayName("Escalation log captures correct trigger rule and score")
    void escalationLog_capturesCorrectData() {
        ArgumentCaptor<EscalationLog> logCaptor = ArgumentCaptor.forClass(EscalationLog.class);

        engine.evaluate(1L, 92);

        verify(escalationLogRepository).save(logCaptor.capture());
        EscalationLog capturedLog = logCaptor.getValue();

        assertEquals("single_85", capturedLog.getTriggerRule());
        assertEquals(92, capturedLog.getRiskScore());
        assertEquals(testSession, capturedLog.getSession());
        assertEquals(testUser, capturedLog.getUser());
        assertTrue(capturedLog.getIsActive());
    }
}
