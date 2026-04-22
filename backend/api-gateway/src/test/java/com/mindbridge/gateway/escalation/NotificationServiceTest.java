package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.NotificationOutbox;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.TherapistQueue;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.NotificationOutboxRepository;
import com.mindbridge.core.repository.TherapistQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService}.
 */
class NotificationServiceTest {

    private TherapistQueueRepository therapistQueueRepository;
    private NotificationOutboxRepository notificationOutboxRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        therapistQueueRepository = Mockito.mock(TherapistQueueRepository.class);
        notificationOutboxRepository = Mockito.mock(NotificationOutboxRepository.class);
        notificationService = new NotificationService(
                therapistQueueRepository, notificationOutboxRepository);

        when(therapistQueueRepository.save(any(TherapistQueue.class)))
                .thenAnswer(i -> {
                    TherapistQueue q = i.getArgument(0);
                    q.setId(1L);
                    return q;
                });
        when(notificationOutboxRepository.save(any(NotificationOutbox.class)))
                .thenAnswer(i -> {
                    NotificationOutbox n = i.getArgument(0);
                    n.setId(1L);
                    return n;
                });
    }

    private EscalationLog createTestLog() {
        User user = new User();
        user.setId(1L);

        Session session = new Session();
        session.setId(10L);
        session.setUser(user);

        EscalationLog log = new EscalationLog(session, user, "single_85", 90);
        log.setId(100L);
        return log;
    }

    @Test
    @DisplayName("Notify creates therapist queue entry")
    void notify_createsTherapistQueueEntry() {
        EscalationLog log = createTestLog();

        notificationService.notify(log);

        ArgumentCaptor<TherapistQueue> captor = ArgumentCaptor.forClass(TherapistQueue.class);
        verify(therapistQueueRepository).save(captor.capture());

        TherapistQueue saved = captor.getValue();
        assertEquals(log, saved.getEscalation());
        assertEquals("pending", saved.getStatus());
    }

    @Test
    @DisplayName("Notify creates email and SMS outbox entries")
    void notify_createsEmailAndSmsOutbox() {
        EscalationLog log = createTestLog();

        notificationService.notify(log);

        ArgumentCaptor<NotificationOutbox> captor =
                ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(notificationOutboxRepository, times(2)).save(captor.capture());

        List<NotificationOutbox> saved = captor.getAllValues();
        assertEquals(2, saved.size());

        // One email, one SMS
        NotificationOutbox email = saved.stream()
                .filter(n -> "email".equals(n.getChannel())).findFirst().orElse(null);
        NotificationOutbox sms = saved.stream()
                .filter(n -> "sms".equals(n.getChannel())).findFirst().orElse(null);

        assertNotNull(email, "Email notification should be created");
        assertNotNull(sms, "SMS notification should be created");

        assertTrue(email.getPayload().contains("CRISIS ESCALATION"));
        assertTrue(email.getSubject().contains("Session #10"));
        assertEquals("oncall-therapist@mindbridge.ai", email.getRecipient());

        assertTrue(sms.getPayload().contains("Session #10"));
        assertFalse(email.getSent());
        assertFalse(sms.getSent());
    }

    @Test
    @DisplayName("Notify completes quickly (< 1 second)")
    void notify_completesQuickly() {
        EscalationLog log = createTestLog();

        long start = System.currentTimeMillis();
        notificationService.notify(log);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1000, "Notification should complete in < 1s, took: " + elapsed + "ms");
    }
}
