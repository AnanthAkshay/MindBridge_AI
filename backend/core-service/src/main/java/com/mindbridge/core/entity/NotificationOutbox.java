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
 * Notifications outbox entity — simulated email/SMS payloads.
 *
 * <p>Persists notification payloads for future delivery. Actual sending
 * is out of scope; this table acts as a transactional outbox for
 * reliable notification dispatch.</p>
 */
@Entity
@Table(name = "notifications_outbox")
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalation_id", nullable = false)
    private EscalationLog escalation;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String channel;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Boolean sent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public NotificationOutbox() {}

    /**
     * Construct a new notification outbox entry.
     *
     * @param escalation the triggering escalation
     * @param channel    delivery channel ("email" or "sms")
     * @param recipient  the recipient address/number
     * @param subject    email subject (nullable for SMS)
     * @param payload    the notification body
     */
    public NotificationOutbox(EscalationLog escalation, String channel,
                               String recipient, String subject, String payload) {
        this.escalation = escalation;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.payload = payload;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EscalationLog getEscalation() { return escalation; }
    public void setEscalation(EscalationLog escalation) { this.escalation = escalation; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Boolean getSent() { return sent; }
    public void setSent(Boolean sent) { this.sent = sent; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
