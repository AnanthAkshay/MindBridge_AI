package com.mindbridge.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Escalation log entity — records every crisis escalation event.
 *
 * <p>An escalation is fired when risk thresholds are breached:
 * either 3 consecutive messages scoring ≥ 65, or a single message
 * scoring ≥ 85. The {@code is_active} flag enables deduplication —
 * only one active escalation per session at a time.</p>
 */
@Entity
@Table(name = "escalation_log")
public class EscalationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @NotBlank
    @Column(name = "trigger_rule", nullable = false, length = 50)
    private String triggerRule;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public EscalationLog() {}

    /**
     * Construct a new escalation log entry.
     *
     * @param session     the session that triggered escalation
     * @param user        the user (nullable for anonymous sessions)
     * @param triggerRule the rule that was breached ("consecutive_3" or "single_85")
     * @param riskScore   the risk score at the time of escalation
     */
    public EscalationLog(Session session, User user, String triggerRule, Integer riskScore) {
        this.session = session;
        this.user = user;
        this.triggerRule = triggerRule;
        this.riskScore = riskScore;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getTriggerRule() { return triggerRule; }
    public void setTriggerRule(String triggerRule) { this.triggerRule = triggerRule; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
