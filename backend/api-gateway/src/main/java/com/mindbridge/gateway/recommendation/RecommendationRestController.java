package com.mindbridge.gateway.recommendation;

import com.mindbridge.core.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationRestController {

    private final RecommendationService recommendationService;

    public RecommendationRestController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/suggest")
    public ResponseEntity<List<RecommendationService.InterventionCard>> suggest(
            @RequestBody RecommendationService.RecommendationRequest request,
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        List<RecommendationService.InterventionCard> suggestions = recommendationService.suggestInterventions(user.getId(), request);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/{contentId}/complete")
    public ResponseEntity<Void> markComplete(
            @PathVariable String contentId,
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        recommendationService.markCompleted(user.getId(), contentId);
        return ResponseEntity.ok().build();
    }
}
