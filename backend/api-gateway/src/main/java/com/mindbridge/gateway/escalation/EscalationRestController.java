package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.EscalationLog;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EscalationLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for escalation log retrieval.
 *
 * <p>Provides authenticated access to escalation history for a user.
 * Reuses the existing Spring Security authentication from earlier steps.</p>
 */
@RestController
@RequestMapping("/api/escalations")
public class EscalationRestController {

    private final EscalationLogRepository escalationLogRepository;

    /**
     * Construct the controller.
     *
     * @param escalationLogRepository repository for escalation log queries
     */
    public EscalationRestController(EscalationLogRepository escalationLogRepository) {
        this.escalationLogRepository = escalationLogRepository;
    }

    /**
     * Get all escalation logs for a user.
     *
     * <p>Requires authentication. The requesting user can only view their own
     * escalation history (the userId path variable must match the authenticated
     * user's ID).</p>
     *
     * @param userId the user ID to query
     * @param user   the authenticated user (injected by Spring Security)
     * @return response containing escalation list and active count
     */
    @GetMapping("/{userId}")
    public ResponseEntity<EscalationResponse> getEscalations(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {

        // Authorization: users can only view their own escalations
        if (user == null || !user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        List<EscalationLog> escalations = escalationLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        long activeCount = escalationLogRepository
                .countByUserIdAndIsActiveTrue(userId);

        List<EscalationLogDto> dtos = escalations.stream()
                .map(EscalationLogDto::from)
                .toList();

        return ResponseEntity.ok(new EscalationResponse(dtos, activeCount));
    }

    /**
     * Response body for the GET /api/escalations/:userId endpoint.
     *
     * @param escalations list of escalation log entries
     * @param activeCount count of currently active (un-resolved) escalations
     */
    public record EscalationResponse(
            List<EscalationLogDto> escalations,
            long activeCount
    ) {}

    /**
     * DTO for serialising escalation log entries to JSON.
     *
     * @param id          the escalation log ID
     * @param sessionId   the session ID
     * @param userId      the user ID (null for anonymous)
     * @param triggerRule the rule that triggered the escalation
     * @param riskScore   the risk score at escalation time
     * @param createdAt   creation timestamp
     * @param resolvedAt  resolution timestamp (null if still active)
     * @param isActive    whether the escalation is still active
     */
    public record EscalationLogDto(
            Long id,
            Long sessionId,
            Long userId,
            String triggerRule,
            Integer riskScore,
            String createdAt,
            String resolvedAt,
            Boolean isActive
    ) {
        /**
         * Map an entity to a DTO.
         *
         * @param log the escalation log entity
         * @return a new DTO
         */
        public static EscalationLogDto from(EscalationLog log) {
            return new EscalationLogDto(
                    log.getId(),
                    log.getSession() != null ? log.getSession().getId() : null,
                    log.getUser() != null ? log.getUser().getId() : null,
                    log.getTriggerRule(),
                    log.getRiskScore(),
                    log.getCreatedAt() != null ? log.getCreatedAt().toString() : null,
                    log.getResolvedAt() != null ? log.getResolvedAt().toString() : null,
                    log.getIsActive()
            );
        }
    }
}
