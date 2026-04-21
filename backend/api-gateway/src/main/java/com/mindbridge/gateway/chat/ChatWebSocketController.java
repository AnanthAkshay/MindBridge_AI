package com.mindbridge.gateway.chat;

import com.mindbridge.core.entity.User;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * WebSocket STOMP controller for real-time chat.
 *
 * Routing:
 *   Client → /app/chat.send       → broadcasts to /topic/session.{id}
 *   Client → /app/chat.typing     → broadcasts typing indicator
 */
@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NlpServiceClient nlpClient;
    private final MemoryService memoryService;
    private final ClaudeAiClient claudeClient;

    public ChatWebSocketController(
            ChatService chatService, 
            SimpMessagingTemplate messagingTemplate, 
            NlpServiceClient nlpClient, 
            MemoryService memoryService,
            ClaudeAiClient claudeClient) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.nlpClient = nlpClient;
        this.memoryService = memoryService;
        this.claudeClient = claudeClient;
    }

    public record StreamDelta(String messageId, String content, boolean done) {}

    /**
     * Handle inbound chat message.
     * - Validates session ownership
     * - Encrypts and persists to DB
     * - Broadcasts decrypted response to all session subscribers
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessageRequest request, Authentication auth) {
        User user = (User) auth.getPrincipal();

        // Verify session ownership
        if (!chatService.isSessionOwnedByUser(request.sessionId(), user.getId())) {
            throw new SecurityException("Not authorized for this session");
        }

        // Execute NLP asynchronously, then persist and broadcast on completion
        nlpClient.analyzeAsync(request.content()).subscribe(nlp -> {
            ChatMessageResponse response = chatService.saveAndEncrypt(
                    request.sessionId(), "USER", request.content(), nlp
            );

            messagingTemplate.convertAndSend(
                    "/topic/session." + request.sessionId(),
                    response
            );

            // Trigger true streaming Claude response
            triggerClaudeResponseStream(request.sessionId(), request.content(), nlp, user.getId());
        });
    }

    /**
     * Handle typing indicator.
     * Broadcasts to all subscribers of the session's typing topic.
     */
    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingEvent event, Authentication auth) {
        User user = (User) auth.getPrincipal();

        TypingEvent broadcast = new TypingEvent(
                event.sessionId(),
                user.getId(),
                user.getFullName(),
                event.typing(),
                Instant.now()
        );

        messagingTemplate.convertAndSend(
                "/topic/session." + event.sessionId() + ".typing",
                broadcast
        );
    }

    /**
     * Executes Claude API Call and streams directly to STOMP WebSocket.
     */
    private void triggerClaudeResponseStream(Long sessionId, String userMessage, NlpServiceClient.NlpResponse nlp, Long userId) {
        
        // 1. Fetch DB Memory Context concurrently 
        MemoryService.MemoryInsight memory = memoryService.getUserMemoryContext(userId);
        
        // 2. Broadcast Start Typing
        messagingTemplate.convertAndSend(
                "/topic/session." + sessionId + ".typing",
                new TypingEvent(sessionId, 0L, "MindBridge AI", true, Instant.now())
        );

        StringBuilder fullResponseBuilder = new StringBuilder();
        String tempMessageId = "msg_stream_" + UUID.randomUUID().toString();

        claudeClient.streamEmpatheticResponse(userMessage, nlp.emotion(), nlp.valence(), memory, List.of())
            .doOnNext(chunk -> {
                // Instantly pipe chunk backwards to STOMP
                fullResponseBuilder.append(chunk);
                messagingTemplate.convertAndSend(
                        "/topic/session." + sessionId + ".stream",
                        new StreamDelta(tempMessageId, chunk, false)
                );
            })
            .doOnComplete(() -> {
                // Done writing chunks to UI. Stop typing indicator
                messagingTemplate.convertAndSend(
                        "/topic/session." + sessionId + ".typing",
                        new TypingEvent(sessionId, 0L, "MindBridge AI", false, Instant.now())
                );
                
                String finalResponse = fullResponseBuilder.toString();
                
                // Fire final NLP scan on AI text, physically persist it via AES encryption
                NlpServiceClient.NlpResponse aiNlp = nlpClient.analyzeSync(finalResponse);
                ChatMessageResponse aiSavedMsg = chatService.saveAndEncrypt(
                        sessionId, "AI", finalResponse, aiNlp
                );
                
                // Dispatch terminal event so React UI can swap streaming buffer for real DB record
                messagingTemplate.convertAndSend(
                        "/topic/session." + sessionId + ".stream",
                        new StreamDelta(tempMessageId, "", true)
                );
                
                // Broadcast official message event
                messagingTemplate.convertAndSend(
                        "/topic/session." + sessionId,
                        aiSavedMsg
                );
            })
            .doOnError(err -> {
                messagingTemplate.convertAndSend(
                        "/topic/session." + sessionId + ".typing",
                        new TypingEvent(sessionId, 0L, "MindBridge AI", false, Instant.now())
                );
            })
            .subscribe(); // Launch flux
    }
}
