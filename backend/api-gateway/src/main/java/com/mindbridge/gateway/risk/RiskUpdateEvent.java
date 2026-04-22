package com.mindbridge.gateway.risk;

/**
 * WebSocket event payload for risk score updates.
 *
 * <p>Broadcast to {@code /topic/session.{id}.risk} whenever a new
 * risk score is computed for a session.</p>
 *
 * @param type      always {@code "risk_update"}
 * @param score     the risk score (0–100)
 * @param level     the risk level ({@code "LOW"}, {@code "MODERATE"}, or {@code "HIGH"})
 * @param sessionId the session identifier as a string
 */
public record RiskUpdateEvent(
    String type,
    int score,
    String level,
    String sessionId
) {}
