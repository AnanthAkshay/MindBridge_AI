package com.mindbridge.gateway.recommendation;

import com.mindbridge.core.entity.RecommendationLog;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.repository.RecommendationLogRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final RecommendationLogRepository logRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    public RecommendationService(RecommendationLogRepository logRepository, UserRepository userRepository, SessionRepository sessionRepository) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    public record RecommendationRequest(String emotion, int riskScore, Long sessionId) {}
    
    public record InterventionCard(String id, String type, String title, String duration, String description, List<String> steps) {}

    // Behavioral Context Library
    private static final List<InterventionCard> LIBRARY = List.of(
            new InterventionCard("breath_01", "breathing", "Box Breathing (4-4-4-4)", "2 min", "Inhale 4s, hold 4s, exhale 4s, hold 4s. Instantly signals the parasympathetic nervous system to disengage fight-or-flight responses.", List.of()),
            new InterventionCard("breath_02", "breathing", "4-7-8 Breathing Protocol", "3 min", "Scientifically measured to drop resting heart rate, perfect for acute physiological anxiety or insomnia.", List.of()),
            new InterventionCard("cbt_01", "cbt", "Cognitive Thought Reframing", "5 min", "Analyze a distressing automatic thought and actively restructure it sequentially.", List.of("1. Write down the negative thought exactly as you hear it.", "2. What is the explicit physical evidence supporting this?", "3. What is a more balanced, objectively true alternative?")),
            new InterventionCard("cbt_02", "cbt", "Gratitude & Positivity Anchors", "3 min", "For depressive looping. Shift neuroplastic focus towards positive anchors.", List.of("1. Name a basic need that was met today.", "2. Name someone who structurally supports you.", "3. Name one small win, no matter how insignificant.")),
            new InterventionCard("cbt_03", "cbt", "The 5-4-3-2-1 Grounding Method", "4 min", "Sensory override for dissociative or panic spirals.", List.of("Acknowledge 5 things you can visually see right now.", "Touch 4 physical items around you. Notice the texture.", "Listen for 3 distinct sounds in the room.", "Identify 2 things you can smell.", "Name 1 thing you can taste.")),
            new InterventionCard("crisis_01", "crisis", "Emergency Support Protocol", "0 min", "Direct, confidential mental health emergency resources continuously staffed 24/7.", List.of("Call or Text 988 (National Suicide Prevention)", "Text HOME to 741741 (Crisis Text Line)"))
    );

    @Transactional
    public List<InterventionCard> suggestInterventions(Long userId, RecommendationRequest req) {
        List<InterventionCard> suggestions = new ArrayList<>();
        
        // 1. Hard Crisis Override
        if (req.riskScore() >= 65) {
            suggestions.add(getCard("crisis_01"));
        } else if (req.emotion().contains("anxious") || req.emotion().contains("fear") || req.emotion().contains("panic")) {
            suggestions.add(getCard("breath_01"));
            suggestions.add(getCard("cbt_03"));
        } else if (req.emotion().contains("sad") || req.emotion().contains("hopeless") || req.emotion().contains("depressed")) {
            suggestions.add(getCard("cbt_02"));
            suggestions.add(getCard("cbt_01"));
        } else if (req.emotion().contains("angry") || req.emotion().contains("stress")) {
            suggestions.add(getCard("breath_02"));
            suggestions.add(getCard("cbt_01"));
        }

        if (suggestions.isEmpty()) {
            return suggestions;
        }

        // 2. Global Personalization: Filter Duplicates mapped in the last 72 Hours
        Instant threshold = Instant.now().minus(3, ChronoUnit.DAYS);
        List<String> explicitlySeenRecently = logRepository.findRecentRecommendationsForUser(userId, threshold)
                .stream().map(RecommendationLog::getContentId).toList();

        List<InterventionCard> finalSuggestions = suggestions.stream()
                .filter(card -> !explicitlySeenRecently.contains(card.id()) || card.type().equals("crisis"))

                .limit(2) // Max 2 per suggestion pull
                .collect(Collectors.toList());

        // 3. Log them implicitly
        Session session = req.sessionId() != null ? sessionRepository.findById(req.sessionId()).orElse(null) : null;
        for (InterventionCard card : finalSuggestions) {
            RecommendationLog log = new RecommendationLog();
            log.setUser(userRepository.findById(userId).orElseThrow());
            log.setSession(session);
            log.setContentId(card.id());
            log.setContentType(card.type());
            logRepository.save(log);
        }

        return finalSuggestions;
    }

    @Transactional
    public void markCompleted(Long userId, String contentId) {
        logRepository.findByUserIdAndContentIdAndCompletedFalse(userId, contentId).ifPresent(log -> {
            log.setCompleted(true);
            log.setCompletedAt(Instant.now());
            logRepository.save(log);
        });
    }

    private InterventionCard getCard(String id) {
        return LIBRARY.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
}
