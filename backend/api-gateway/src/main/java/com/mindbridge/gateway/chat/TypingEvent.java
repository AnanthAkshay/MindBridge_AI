package com.mindbridge.gateway.chat;

import java.time.Instant;

/**
 * Typing indicator event broadcast to session topic.
 */
public record TypingEvent(
    Long sessionId,
    Long userId,
    String fullName,
    boolean typing,
    Instant timestamp
) {}
