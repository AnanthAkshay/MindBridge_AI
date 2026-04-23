package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.AuditLog;
import com.mindbridge.core.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService}.
 *
 * <p>Validates audit log persistence for sensitive operations
 * and confirms failure isolation (audit errors don't crash the request).</p>
 */
class AuditServiceTest {

    private AuditLogRepository auditLogRepository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        auditService = new AuditService(auditLogRepository);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> {
            AuditLog log = i.getArgument(0);
            log.setId(1L);
            return log;
        });
    }

    /**
     * AT3: Trigger any auth event → confirm a row exists in audit_log.
     */
    @Test
    @DisplayName("AT3: Auth event → audit log row created")
    void authEvent_createsAuditRow() {
        auditService.log("USER_LOGIN", 1L, null, "192.168.1.1", "Mozilla/5.0", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("USER_LOGIN", saved.getAction());
        assertEquals(1L, saved.getActorId());
        assertEquals("192.168.1.1", saved.getIpAddress());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("Audit log captures all field types")
    void auditLog_capturesAllFields() {
        String metadata = "{\"action\":\"GDPR_SELF_DELETE\",\"userId\":42}";
        auditService.log("GDPR_SELF_DELETE", 42L, 42L, "10.0.0.1", "TestAgent/1.0", metadata);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("GDPR_SELF_DELETE", saved.getAction());
        assertEquals(42L, saved.getActorId());
        assertEquals(42L, saved.getTargetId());
        assertEquals("10.0.0.1", saved.getIpAddress());
        assertEquals("TestAgent/1.0", saved.getUserAgent());
        assertTrue(saved.getMetadata().contains("GDPR_SELF_DELETE"));
    }

    @Test
    @DisplayName("Audit failure does not throw exception")
    void auditFailure_doesNotThrow() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        // Should not throw
        assertDoesNotThrow(() ->
                auditService.log("TEST_ACTION", 1L, null, null, null, null));
    }

    @Test
    @DisplayName("Anonymous action logs with null actor")
    void anonymousAction_nullActor() {
        auditService.log("ANONYMOUS_LOGIN", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertNull(captor.getValue().getActorId());
        assertEquals("ANONYMOUS_LOGIN", captor.getValue().getAction());
    }
}
