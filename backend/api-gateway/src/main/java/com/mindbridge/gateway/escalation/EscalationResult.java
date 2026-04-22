package com.mindbridge.gateway.escalation;

import com.mindbridge.core.entity.RiskLevel;

/**
 * Immutable result of an escalation evaluation.
 *
 * @param fired     whether the escalation was actually fired (false if deduplicated)
 * @param reason    human-readable reason string (e.g. "single_85", "consecutive_3", "suppressed")
 * @param sessionId the session ID that triggered the evaluation
 */
public record EscalationResult(
    boolean fired,
    String reason,
    String sessionId
) {

    /**
     * Factory for a fired escalation.
     *
     * @param reason    the trigger rule that caused the escalation
     * @param sessionId the session ID
     * @return a new EscalationResult with fired=true
     */
    public static EscalationResult fired(String reason, Long sessionId) {
        return new EscalationResult(true, reason, String.valueOf(sessionId));
    }

    /**
     * Factory for a suppressed (deduplicated) escalation.
     *
     * @param sessionId the session ID
     * @return a new EscalationResult with fired=false
     */
    public static EscalationResult suppressed(Long sessionId) {
        return new EscalationResult(false, "suppressed: active escalation already exists",
                String.valueOf(sessionId));
    }

    /**
     * Factory for when no escalation condition is met.
     *
     * @param sessionId the session ID
     * @return a new EscalationResult with fired=false
     */
    public static EscalationResult noEscalation(Long sessionId) {
        return new EscalationResult(false, "no escalation threshold breached",
                String.valueOf(sessionId));
    }
}
