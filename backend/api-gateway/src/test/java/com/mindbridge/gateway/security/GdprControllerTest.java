package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.RefreshTokenRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GdprController}.
 *
 * <p>Validates GDPR Article 17 (Right to Erasure) compliance:
 * all user data is hard-deleted and an audit trail is written.</p>
 */
class GdprControllerTest {

    private UserRepository userRepository;
    private SessionRepository sessionRepository;
    private MessageRepository messageRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AuditService auditService;
    private GdprController controller;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        sessionRepository = Mockito.mock(SessionRepository.class);
        messageRepository = Mockito.mock(MessageRepository.class);
        refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
        auditService = Mockito.mock(AuditService.class);

        controller = new GdprController(
                userRepository, sessionRepository, messageRepository,
                refreshTokenRepository, auditService);
    }

    /**
     * AT2: Call DELETE /api/user/me → confirm all user rows are gone.
     */
    @Test
    @DisplayName("AT2: DELETE /api/user/me → all data deleted, returns 204")
    void deleteMyData_deletesAllData_returns204() {
        User user = new User();
        user.setId(42L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("User-Agent", "TestBrowser/1.0");

        var response = controller.deleteMyData(user, request);

        assertEquals(204, response.getStatusCode().value());

        // Verify deletion order
        verify(messageRepository).deleteAllByUserId(42L);
        verify(sessionRepository).deleteByUserId(42L);
        verify(refreshTokenRepository).revokeAllByUserId(42L);
        verify(userRepository).deleteById(42L);

        // Verify audit log was written
        verify(auditService).log(
                eq("GDPR_SELF_DELETE"),
                eq(42L), eq(42L),
                eq("192.168.1.100"),
                eq("TestBrowser/1.0"),
                contains("USER_SELF_DELETE"));
    }

    @Test
    @DisplayName("Unauthenticated request → 401")
    void deleteMyData_noAuth_returns401() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = controller.deleteMyData(null, request);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    @DisplayName("GDPR delete completes quickly")
    void deleteMyData_completesQuickly() {
        User user = new User();
        user.setId(1L);

        MockHttpServletRequest request = new MockHttpServletRequest();

        long start = System.currentTimeMillis();
        controller.deleteMyData(user, request);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 2000, "GDPR delete should complete in < 2s, took: " + elapsed + "ms");
    }
}
