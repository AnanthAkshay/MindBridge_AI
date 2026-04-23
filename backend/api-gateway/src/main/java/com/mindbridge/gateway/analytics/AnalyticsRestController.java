package com.mindbridge.gateway.analytics;

import com.mindbridge.core.entity.EmotionMemory;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.EmotionMemoryRepository;
import com.mindbridge.core.repository.SessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsRestController {

    private final SessionRepository sessionRepository;
    private final EmotionMemoryRepository emotionMemoryRepository;

    public AnalyticsRestController(SessionRepository sessionRepository, EmotionMemoryRepository emotionMemoryRepository) {
        this.sessionRepository = sessionRepository;
        this.emotionMemoryRepository = emotionMemoryRepository;
    }

    @GetMapping("/{userId}/mood-trend")
    public ResponseEntity<List<MoodTrendDto>> getMoodTrend(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {

        if (user == null || !user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Session> recentSessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(s -> s.getCreatedAt().isAfter(sevenDaysAgo) && s.getMoodScore() != null)
                .toList();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd").withZone(ZoneId.systemDefault());

        Map<String, List<Integer>> dailyScores = new LinkedHashMap<>();
        
        // Initialize last 7 days
        for (int i = 6; i >= 0; i--) {
            String day = formatter.format(Instant.now().minus(i, ChronoUnit.DAYS));
            dailyScores.put(day, new ArrayList<>());
        }

        for (Session session : recentSessions) {
            String day = formatter.format(session.getCreatedAt());
            if (dailyScores.containsKey(day)) {
                dailyScores.get(day).add(session.getMoodScore());
            }
        }

        List<MoodTrendDto> trend = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : dailyScores.entrySet()) {
            double avg = entry.getValue().isEmpty() ? 0 : 
                    entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
            trend.add(new MoodTrendDto(entry.getKey(), Math.round(avg * 10.0) / 10.0));
        }

        return ResponseEntity.ok(trend);
    }

    @GetMapping("/{userId}/emotion-distribution")
    public ResponseEntity<List<EmotionDistributionDto>> getEmotionDistribution(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {

        if (user == null || !user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        List<EmotionMemory> memories = emotionMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Long> counts = memories.stream()
                .filter(m -> m.getDominantEmotion() != null)
                .collect(Collectors.groupingBy(EmotionMemory::getDominantEmotion, Collectors.counting()));

        List<EmotionDistributionDto> distribution = counts.entrySet().stream()
                .map(e -> new EmotionDistributionDto(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.value(), a.value()))
                .toList();

        return ResponseEntity.ok(distribution);
    }

    @GetMapping("/{userId}/session-timeline")
    public ResponseEntity<List<SessionTimelineDto>> getSessionTimeline(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {

        if (user == null || !user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        List<Session> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<SessionTimelineDto> timeline = sessions.stream()
                .map(s -> new SessionTimelineDto(
                        s.getId(),
                        s.getCreatedAt().toString(),
                        s.getEndedAt() != null ? s.getEndedAt().toString() : null,
                        s.getMoodScore(),
                        s.getRiskScore()
                ))
                .limit(10) // Limit to last 10 for dashboard timeline
                .toList();

        return ResponseEntity.ok(timeline);
    }

    public record MoodTrendDto(String date, double averageMood) {}
    public record EmotionDistributionDto(String name, long value) {}
    public record SessionTimelineDto(Long sessionId, String startedAt, String endedAt, Integer moodScore, Integer riskScore) {}
}
