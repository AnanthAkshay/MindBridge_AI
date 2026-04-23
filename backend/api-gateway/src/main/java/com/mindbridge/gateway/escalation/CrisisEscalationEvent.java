package com.mindbridge.gateway.escalation;

import java.util.List;

/**
 * WebSocket event payload for crisis escalation banners.
 *
 * <p>Broadcast to {@code /topic/session.{id}.crisis} when an
 * escalation is fired. The frontend renders a dismissible crisis
 * banner with helpline information.</p>
 *
 * @param type      always {@code "crisis_escalation"}
 * @param sessionId the session identifier as a string
 * @param helplines list of crisis helpline descriptions
 * @param message   empathetic message shown in the crisis banner
 */
public record CrisisEscalationEvent(
    String type,
    String sessionId,
    List<String> helplines,
    String message
) {

    /** Standard crisis helplines included in every escalation event. */
    public static final List<String> DEFAULT_HELPLINES = List.of(
            "988 Suicide & Crisis Lifeline",
            "Crisis Text Line: text HOME to 741741"
    );

    /** Standard empathetic message for crisis banners. */
    public static final String DEFAULT_MESSAGE =
            "We noticed you may be going through a difficult time. Help is available.";

    /**
     * Create a crisis escalation event with default helplines and message.
     *
     * @param sessionId the session ID
     * @return a new CrisisEscalationEvent with defaults
     */
    public static CrisisEscalationEvent withDefaults(Long sessionId) {
        return new CrisisEscalationEvent(
                "crisis_escalation",
                String.valueOf(sessionId),
                DEFAULT_HELPLINES,
                DEFAULT_MESSAGE
        );
    }
}
