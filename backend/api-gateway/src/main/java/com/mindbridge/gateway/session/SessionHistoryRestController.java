package com.mindbridge.gateway.session;

import com.mindbridge.core.entity.EmotionMemory;
import com.mindbridge.core.entity.Message;
import com.mindbridge.core.entity.RecommendationLog;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EmotionMemoryRepository;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.RecommendationLogRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.service.MessageEncryptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
public class SessionHistoryRestController {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final EmotionMemoryRepository emotionMemoryRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final MessageEncryptionService encryptionService;

    public SessionHistoryRestController(
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            EmotionMemoryRepository emotionMemoryRepository,
            RecommendationLogRepository recommendationLogRepository,
            MessageEncryptionService encryptionService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.emotionMemoryRepository = emotionMemoryRepository;
        this.recommendationLogRepository = recommendationLogRepository;
        this.encryptionService = encryptionService;
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<SessionHistorySummaryDto>> getSessionHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User user) {

        if (user == null || !user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        List<Session> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (keyword != null && !keyword.isBlank()) {
            sessions = sessions.stream()
                    .filter(s -> s.getTitle() != null && s.getTitle().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        }

        List<SessionHistorySummaryDto> dtos = sessions.stream().map(s -> {
            Optional<EmotionMemory> emotionMemory = emotionMemoryRepository.findBySessionId(s.getId());
            List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId());
            List<Double> emotionArc = messages.stream()
                    .filter(m -> m.getValence() != null)
                    .map(Message::getValence)
                    .toList();

            return new SessionHistorySummaryDto(
                    s.getId(),
                    s.getTitle(),
                    s.getStartedAt().toString(),
                    s.getEndedAt() != null ? s.getEndedAt().toString() : null,
                    emotionMemory.map(EmotionMemory::getDominantEmotion).orElse("Unknown"),
                    emotionArc
            );
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{userId}/history/{sessionId}")
    public ResponseEntity<SessionDetailDto> getSessionDetail(
            @PathVariable Long userId,
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user) {

        if (user == null || !user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || !sessionOpt.get().getUser().getId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        Session session = sessionOpt.get();
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        Optional<EmotionMemory> emotionMemory = emotionMemoryRepository.findBySessionId(sessionId);
        List<RecommendationLog> recommendations = recommendationLogRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);

        List<MessageDto> messageDtos = messages.stream().map(m -> {
            String decryptedContent = "";
            try {
                decryptedContent = encryptionService.decrypt(m.getEncryptedContent(), m.getEncryptionIv());
            } catch (Exception e) {
                decryptedContent = "[Decryption Failed]";
            }
            return new MessageDto(m.getSenderType(), decryptedContent, m.getCreatedAt().toString(), m.getValence(), m.getArousal());
        }).toList();

        List<RecommendationDto> recDtos = recommendations.stream()
                .map(r -> new RecommendationDto(r.getRecommendationType(), r.getContent(), r.getCreatedAt().toString()))
                .toList();

        return ResponseEntity.ok(new SessionDetailDto(
                session.getId(),
                session.getTitle(),
                emotionMemory.map(EmotionMemory::getDominantEmotion).orElse("Unknown"),
                emotionMemory.map(EmotionMemory::getSummaryText).orElse(""),
                messageDtos,
                recDtos
        ));
    }

    public record SessionHistorySummaryDto(Long sessionId, String title, String startedAt, String endedAt, String dominantEmotion, List<Double> emotionArc) {}
    public record MessageDto(String senderType, String content, String createdAt, Double valence, Double arousal) {}
    public record RecommendationDto(String type, String content, String createdAt) {}
    public record SessionDetailDto(Long sessionId, String title, String dominantEmotion, String summary, List<MessageDto> messages, List<RecommendationDto> recommendations) {}
}
