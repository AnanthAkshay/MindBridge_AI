package com.mindbridge.gateway.therapist;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.Message;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.service.MessageEncryptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/therapist")
public class TherapistRestController {

    private final EscalationLogRepository escalationLogRepository;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MessageEncryptionService encryptionService;

    public TherapistRestController(
            EscalationLogRepository escalationLogRepository,
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            MessageEncryptionService encryptionService) {
        this.escalationLogRepository = escalationLogRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.encryptionService = encryptionService;
    }

    @GetMapping("/escalations")
    public ResponseEntity<List<EscalationSummaryDto>> getEscalations(
            @AuthenticationPrincipal User user) {

        if (user == null || !hasTherapistRole(user)) {
            return ResponseEntity.status(403).build();
        }

        List<EscalationLog> escalations = escalationLogRepository.findByIsActiveTrueOrderByCreatedAtDesc();

        List<EscalationSummaryDto> dtos = escalations.stream().map(e -> new EscalationSummaryDto(
                e.getId(),
                e.getSession() != null ? e.getSession().getId() : null,
                e.getUser() != null ? e.getUser().getId() : null,
                e.getTriggerRule(),
                e.getRiskScore(),
                e.getCreatedAt().toString()
        )).toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/escalations/{id}/resolve")
    public ResponseEntity<Void> resolveEscalation(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        if (user == null || !hasTherapistRole(user)) {
            return ResponseEntity.status(403).build();
        }

        Optional<EscalationLog> logOpt = escalationLogRepository.findById(id);
        if (logOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EscalationLog log = logOpt.get();
        log.setIsActive(false);
        log.setResolvedAt(Instant.now());
        escalationLogRepository.save(log);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions/{sessionId}/transcript")
    public ResponseEntity<TranscriptDto> getTranscript(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user) {

        if (user == null || !hasTherapistRole(user)) {
            return ResponseEntity.status(403).build();
        }

        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Session session = sessionOpt.get();
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        List<TranscriptMessageDto> messageDtos = messages.stream().map(m -> {
            String decrypted = "";
            try {
                decrypted = encryptionService.decrypt(m.getEncryptedContent(), m.getEncryptionIv());
            } catch (Exception e) {
                decrypted = "[Decryption Error]";
            }
            return new TranscriptMessageDto(m.getSenderType(), decrypted, m.getCreatedAt().toString());
        }).toList();

        return ResponseEntity.ok(new TranscriptDto(
                session.getId(),
                session.getUser() != null ? session.getUser().getId() : null,
                messageDtos
        ));
    }

    private boolean hasTherapistRole(User user) {
        // Assuming user role string or granted authorities. We check if email or a role implies therapist.
        // In the absence of a strict Role enum with THERAPIST, we can check role string.
        return user.getRole() != null && user.getRole().toUpperCase().contains("THERAPIST");
    }

    public record EscalationSummaryDto(Long id, Long sessionId, Long userId, String triggerRule, Integer riskScore, String createdAt) {}
    public record TranscriptMessageDto(String senderType, String content, String createdAt) {}
    public record TranscriptDto(Long sessionId, Long userId, List<TranscriptMessageDto> messages) {}
}
