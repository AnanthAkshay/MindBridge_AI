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

import java.time.Instant;

/**
 * Therapist queue entity — pending escalations for therapist review.
 *
 * <p>Each entry links to an {@link EscalationLog} record and tracks
 * whether a therapist has reviewed or closed the escalation.</p>
 */
@Entity
@Table(name = "therapist_queue")
public class TherapistQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalation_id", nullable = false)
    private EscalationLog escalation;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TherapistQueue() {}

    /**
     * Construct a new therapist queue entry.
     *
     * @param escalation the escalation log entry this queue item references
     */
    public TherapistQueue(EscalationLog escalation) {
        this.escalation = escalation;
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EscalationLog getEscalation() { return escalation; }
    public void setEscalation(EscalationLog escalation) { this.escalation = escalation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
