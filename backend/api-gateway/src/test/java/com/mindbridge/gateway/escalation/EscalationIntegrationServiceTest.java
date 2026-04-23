package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.Message;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.gateway.risk.WeightedRiskAggregator;
import com.mindbridge.core.entity.RiskLevel;
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
 * Integration-level tests for {@link EscalationIntegrationService}
 * verifying the bridge between Step 8 risk pipeline and Step 9 escalation.
 */
class EscalationIntegrationServiceTest {

    private EscalationEngine escalationEngine;
    private MessageRepository messageRepository;
    private EscalationIntegrationService integrationService;

    @BeforeEach
    void setUp() {
        escalationEngine = Mockito.mock(EscalationEngine.class);
        messageRepository = Mockito.mock(MessageRepository.class);
        integrationService = new EscalationIntegrationService(escalationEngine, messageRepository);
    }

    @Test
    @DisplayName("processRiskResult stamps message and delegates to engine")
    void processRiskResult_stampsAndDelegates() {
        // Create a mock message
        Message msg = new Message();
        msg.setId(1L);
        msg.setSenderType("USER");

        when(messageRepository.findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                eq(10L), eq("USER"), any(Pageable.class)))
                .thenReturn(List.of(msg));
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));
        when(escalationEngine.evaluate(10L, 75))
                .thenReturn(EscalationResult.noEscalation(10L));

        WeightedRiskAggregator.RiskResult riskResult =
                new WeightedRiskAggregator.RiskResult(75, RiskLevel.HIGH);

        EscalationResult result = integrationService.processRiskResult(10L, riskResult);

        // Verify message was stamped with risk score
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(msgCaptor.capture());
        assertEquals(75, msgCaptor.getValue().getRiskScore());

        // Verify engine was called
        verify(escalationEngine).evaluate(10L, 75);
        assertFalse(result.fired());
    }

    @Test
    @DisplayName("processRiskResult handles empty message list gracefully")
    void processRiskResult_noMessages_stillEvaluates() {
        when(messageRepository.findBySessionIdAndSenderTypeOrderByCreatedAtDesc(
                eq(10L), eq("USER"), any(Pageable.class)))
                .thenReturn(List.of());
        when(escalationEngine.evaluate(10L, 50))
                .thenReturn(EscalationResult.noEscalation(10L));

        WeightedRiskAggregator.RiskResult riskResult =
                new WeightedRiskAggregator.RiskResult(50, RiskLevel.MODERATE);

        EscalationResult result = integrationService.processRiskResult(10L, riskResult);

        // No message to stamp, but engine should still be called
        verify(messageRepository, never()).save(any(Message.class));
        verify(escalationEngine).evaluate(10L, 50);
    }
}
