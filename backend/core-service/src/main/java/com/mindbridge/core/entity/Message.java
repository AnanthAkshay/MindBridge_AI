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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Message entity — stores AES-256-GCM encrypted content at rest.
 * The 'encryptedContent' is Base64-encoded ciphertext.
 * The 'encryptionIv' is the Base64-encoded initialization vector.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @NotBlank
    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    @NotBlank
    @Column(name = "encrypted_content", nullable = false, columnDefinition = "TEXT")
    private String encryptedContent;

    @NotBlank
    @Column(name = "encryption_iv", nullable = false, length = 64)
    private String encryptionIv;

    @Column(name = "emotion", length = 50)
    private String emotion;

    @Column(name = "emotion_score")
    private Double emotionScore;

    @Column(name = "valence")
    private Double valence;

    @Column(name = "arousal")
    private Double arousal;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Message() {}

    public Message(Session session, String senderType, String encryptedContent, String encryptionIv) {
        this.session = session;
        this.senderType = senderType;
        this.encryptedContent = encryptedContent;
        this.encryptionIv = encryptionIv;
    }

    public Message(Session session, String senderType, String encryptedContent, String encryptionIv, 
                   String emotion, Double emotionScore, Double valence, Double arousal) {
        this.session = session;
        this.senderType = senderType;
        this.encryptedContent = encryptedContent;
        this.encryptionIv = encryptionIv;
        this.emotion = emotion;
        this.emotionScore = emotionScore;
        this.valence = valence;
        this.arousal = arousal;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getEncryptedContent() { return encryptedContent; }
    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    public String getEncryptionIv() { return encryptionIv; }
    public void setEncryptionIv(String encryptionIv) { this.encryptionIv = encryptionIv; }

    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }

    public Double getEmotionScore() { return emotionScore; }
    public void setEmotionScore(Double emotionScore) { this.emotionScore = emotionScore; }

    public Double getValence() { return valence; }
    public void setValence(Double valence) { this.valence = valence; }

    public Double getArousal() { return arousal; }
    public void setArousal(Double arousal) { this.arousal = arousal; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
}
