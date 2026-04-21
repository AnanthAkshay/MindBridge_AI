package com.mindbridge.gateway.chat;

import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for chat session management.
 * WebSocket handles real-time messaging; REST handles session CRUD.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Create a new chat session */
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        Session session = chatService.createSession(user, title);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SessionResponse(session.getId(), session.getTitle(), session.getStatus(), session.getCreatedAt().toString()));
    }

    /** List all sessions for the authenticated user */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(@AuthenticationPrincipal User user) {
        List<SessionResponse> sessions = chatService.getUserSessions(user.getId())
                .stream()
                .map(s -> new SessionResponse(s.getId(), s.getTitle(), s.getStatus(), s.getCreatedAt().toString()))
                .toList();
        return ResponseEntity.ok(sessions);
    }

    /** Get chat history for a specific session (decrypted) */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId) {
        if (!chatService.isSessionOwnedByUser(sessionId, user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(chatService.getSessionHistory(sessionId));
    }

    record SessionResponse(Long id, String title, String status, String createdAt) {}
}
