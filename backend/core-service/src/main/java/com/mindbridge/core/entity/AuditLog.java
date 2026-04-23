package com.mindbridge.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Audit log entity — records all sensitive operations.
 *
 * <p>Logged operations include: login, logout, token refresh,
 * session create, GDPR delete, escalation fire, admin actions.
 * The {@code metadata} field stores arbitrary structured data
 * as a JSONB string.</p>
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "ip_address", columnDefinition = "INET")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditLog() {}

    /**
     * Construct an audit log entry.
     *
     * @param action    the action identifier (e.g. "USER_LOGIN", "GDPR_DELETE")
     * @param actorId   the user who performed the action (null for anonymous)
     * @param targetId  the target entity ID (e.g. deleted user ID)
     * @param ipAddress the client IP address
     * @param userAgent the client user agent string
     * @param metadata  JSON metadata string
     */
    public AuditLog(String action, Long actorId, Long targetId,
                     String ipAddress, String userAgent, String metadata) {
        this.action = action;
        this.actorId = actorId;
        this.targetId = targetId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.metadata = metadata;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
