package com.mindbridge.core.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "emotion_memory")
public class EmotionMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "dominant_emotion", nullable = false, length = 50)
    private String dominantEmotion;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "valence")
    private Double valence;

    @Column(name = "arousal")
    private Double arousal;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "trigger_tag", length = 100)
    private String triggerTag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EmotionMemory() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    public String getDominantEmotion() { return dominantEmotion; }
    public void setDominantEmotion(String dominantEmotion) { this.dominantEmotion = dominantEmotion; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Double getValence() { return valence; }
    public void setValence(Double valence) { this.valence = valence; }
    public Double getArousal() { return arousal; }
    public void setArousal(Double arousal) { this.arousal = arousal; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public String getTriggerTag() { return triggerTag; }
    public void setTriggerTag(String triggerTag) { this.triggerTag = triggerTag; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
