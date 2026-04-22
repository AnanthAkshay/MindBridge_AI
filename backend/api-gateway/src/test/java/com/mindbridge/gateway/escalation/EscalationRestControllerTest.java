package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EscalationRestController}.
 *
 * <p>Validates the REST endpoint logic for retrieving escalation logs.</p>
 */
class EscalationRestControllerTest {

    private EscalationLogRepository escalationLogRepository;
    private EscalationRestController controller;

    @BeforeEach
    void setUp() {
        escalationLogRepository = Mockito.mock(EscalationLogRepository.class);
        controller = new EscalationRestController(escalationLogRepository);
    }

    @Test
    @DisplayName("AT5: GET /api/escalations/:userId returns correct log entries")
    void getEscalations_returnsCorrectEntries() {
        User user = new User();
        user.setId(1L);

        Session session = new Session();
        session.setId(10L);
        session.setUser(user);

        EscalationLog log1 = new EscalationLog(session, user, "single_85", 90);
        log1.setId(1L);
        log1.setCreatedAt(Instant.now());

        EscalationLog log2 = new EscalationLog(session, user, "consecutive_3", 70);
        log2.setId(2L);
        log2.setCreatedAt(Instant.now());
        log2.setIsActive(false);
        log2.setResolvedAt(Instant.now());

        when(escalationLogRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(log1, log2));
        when(escalationLogRepository.countByUserIdAndIsActiveTrue(1L))
                .thenReturn(1L);

        var response = controller.getEscalations(1L, user);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        var body = response.getBody();
        assertEquals(2, body.escalations().size());
        assertEquals(1L, body.activeCount());

        // Verify DTO fields
        var dto1 = body.escalations().get(0);
        assertEquals(1L, dto1.id());
        assertEquals(10L, dto1.sessionId());
        assertEquals("single_85", dto1.triggerRule());
        assertEquals(90, dto1.riskScore());
        assertTrue(dto1.isActive());
    }

    @Test
    @DisplayName("Unauthorized user gets 403")
    void getEscalations_unauthorizedUser_returns403() {
        User user = new User();
        user.setId(1L);

        // Try to access another user's escalations
        var response = controller.getEscalations(999L, user);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Null user gets 403")
    void getEscalations_nullUser_returns403() {
        var response = controller.getEscalations(1L, null);

        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    @DisplayName("User with no escalations returns empty list")
    void getEscalations_noEntries_returnsEmptyList() {
        User user = new User();
        user.setId(1L);

        when(escalationLogRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());
        when(escalationLogRepository.countByUserIdAndIsActiveTrue(1L))
                .thenReturn(0L);

        var response = controller.getEscalations(1L, user);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().escalations().size());
        assertEquals(0, response.getBody().activeCount());
    }
}
