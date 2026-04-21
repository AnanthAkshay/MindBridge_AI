package com.mindbridge.gateway.chat;

import com.mindbridge.core.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/memory")
public class MemoryRestController {

    private final MemoryService memoryService;
    private final ChatService chatService;

    public MemoryRestController(MemoryService memoryService, ChatService chatService) {
        this.memoryService = memoryService;
        this.chatService = chatService;
    }

    /**
     * Retrieve behavioral/emotional history context for the frontend Insight UI.
     * Guaranteed <20ms thanks to DB indexing + caching framework.
     */
    @GetMapping
    public ResponseEntity<MemoryService.MemoryInsight> getMyMemory(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        MemoryService.MemoryInsight insight = memoryService.getUserMemoryContext(user.getId());
        return ResponseEntity.ok(insight);
    }

    /**
     * Triggers the Memory Engined pipeline. In production this would happen automatically
     * on disconnect or cron map, but here we expose it for deterministic testing.
     */
    @PostMapping("/session/{sessionId}/end")
    public ResponseEntity<Void> endSessionAndProcessMemory(@PathVariable Long sessionId, Authentication auth) {
        User user = (User) auth.getPrincipal();
        if (!chatService.isSessionOwnedByUser(sessionId, user.getId())) {
            return ResponseEntity.status(403).build();
        }
        
        memoryService.processSessionMemory(sessionId);
        return ResponseEntity.ok().build();
    }
}
