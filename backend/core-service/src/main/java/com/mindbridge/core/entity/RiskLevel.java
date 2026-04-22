package com.mindbridge.core.entity;

/**
 * Risk classification levels for mental health session scoring.
 *
 * <ul>
 *   <li>{@code LOW}      — Score 0–39: No immediate concern</li>
 *   <li>{@code MODERATE}  — Score 40–64: Elevated concern, monitor closely</li>
 *   <li>{@code HIGH}      — Score 65–100: Crisis-level risk, escalation recommended</li>
 * </ul>
 */
public enum RiskLevel {

    LOW,
    MODERATE,
    HIGH;

    /**
     * Classify a numeric risk score (0–100) into the appropriate risk level.
     *
     * @param score the risk score, expected range 0–100
     * @return the corresponding {@link RiskLevel}
     * @throws IllegalArgumentException if score is outside 0–100
     */
    public static RiskLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be 0–100, got: " + score);
        }
        if (score >= 65) return HIGH;
        if (score >= 40) return MODERATE;
        return LOW;
    }
}
