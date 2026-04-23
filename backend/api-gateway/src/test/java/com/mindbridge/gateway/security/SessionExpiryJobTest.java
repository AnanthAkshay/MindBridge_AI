package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SessionExpiryJob}.
 *
 * <p>Validates TTL-based session expiry with message hard-deletion
 * for GDPR §17 compliance.</p>
 */
class SessionExpiryJobTest {

    private SessionRepository sessionRepository;
    private MessageRepository messageRepository;
    private AuditService auditService;
    private SessionExpiryJob job;

    @BeforeEach
    void setUp() {
        sessionRepository = Mockito.mock(SessionRepository.class);
        messageRepository = Mockito.mock(MessageRepository.class);
        auditService = Mockito.mock(AuditService.class);

        job = new SessionExpiryJob(sessionRepository, messageRepository, auditService, 90);
    }

    @Test
    @DisplayName("Expired sessions are soft-deleted with messages hard-deleted")
    void expiredSessions_processedCorrectly() {
        User user = new User();
        user.setId(1L);

        Session expired = new Session();
        expired.setId(10L);
        expired.setUser(user);
        expired.setStatus("ACTIVE");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(sessionRepository.findExpiredSessions(any(Instant.class)))
                .thenReturn(List.of(expired))
                .thenReturn(List.of()); // second call returns empty (after processing)
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        job.expireSessions();

        // Messages hard-deleted
        verify(messageRepository).deleteBySessionId(10L);

        // Session soft-deleted
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals("DELETED", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getEndedAt());

        // Audit logged
        verify(auditService).log(eq("SESSION_EXPIRED"), isNull(), eq(10L),
                isNull(), isNull(), contains("sessionId"));
    }

    @Test
    @DisplayName("No expired sessions → no processing")
    void noExpiredSessions_noProcessing() {
        when(sessionRepository.findExpiredSessions(any(Instant.class)))
                .thenReturn(List.of());

        job.expireSessions();

        verify(messageRepository, never()).deleteBySessionId(anyLong());
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("TTL default is 90 days")
    void defaultTtl_is90Days() {
        assertEquals(90, job.getTtlDays());
    }
}
