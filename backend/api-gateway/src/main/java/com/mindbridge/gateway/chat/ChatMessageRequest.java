package com.mindbridge.gateway.chat;

/**
 * Inbound WebSocket message from the client.
 */
public record ChatMessageRequest(
    Long sessionId,
    String content,
    Boolean useMemory
) {}
