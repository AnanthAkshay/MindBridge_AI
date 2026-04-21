package com.mindbridge.gateway.chat;

import java.time.Instant;

/**
 * Outbound WebSocket message broadcast to subscribers.
 * Content is decrypted before being sent over the wire.
 */
public record ChatMessageResponse(
    Long id,
    Long sessionId,
    String senderType,
    String content,
    String emotion,
    Double emotionScore,
    Instant createdAt
) {}
