package com.mindbridge.gateway.risk;

import com.mindbridge.gateway.chat.NlpServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TonalEmotionScorer}.
 *
 * <p>Validates emotion-to-risk mapping, valence penalty, confidence
 * boosting, and handling of null/neutral inputs.</p>
 */
class TonalEmotionScorerTest {

    private final TonalEmotionScorer scorer = new TonalEmotionScorer();

    @Test
    @DisplayName("Null NLP response → score is 0")
    void nullResponse_returnsZero() {
        assertEquals(0.0, scorer.score(null));
    }

    @Test
    @DisplayName("Neutral emotion with zero valence → near-zero score")
    void neutralEmotion_nearZeroScore() {
        NlpServiceClient.NlpResponse neutral =
                new NlpServiceClient.NlpResponse("neutral", 0.65, 0.0, 0.0);
        double score = scorer.score(neutral);
        assertTrue(score < 5.0, "Neutral emotion should produce very low score, got: " + score);
    }

    @Test
    @DisplayName("Joy with positive valence → very low score")
    void joyPositiveValence_veryLowScore() {
        NlpServiceClient.NlpResponse joy =
                new NlpServiceClient.NlpResponse("joy", 0.9, 0.9, 0.5);
        double score = scorer.score(joy);
        assertTrue(score < 2.0, "Joy with positive valence should produce minimal score, got: " + score);
    }

    @Test
    @DisplayName("Sadness with negative valence → high score")
    void sadnessNegativeValence_highScore() {
        NlpServiceClient.NlpResponse sadness =
                new NlpServiceClient.NlpResponse("sadness", 0.85, -0.8, -0.4);
        double score = scorer.score(sadness);
        assertTrue(score > 25.0, "Sadness with negative valence should produce high score, got: " + score);
    }

    @Test
    @DisplayName("Grief with negative valence → maximum or near-maximum score")
    void griefNegativeValence_nearMaxScore() {
        NlpServiceClient.NlpResponse grief =
                new NlpServiceClient.NlpResponse("grief", 0.95, -0.9, 0.8);
        double score = scorer.score(grief);
        assertTrue(score >= 35.0, "Grief with high confidence and negative valence should approach max, got: " + score);
    }

    @Test
    @DisplayName("Fear emotion → elevated score")
    void fearEmotion_elevatedScore() {
        NlpServiceClient.NlpResponse fear =
                new NlpServiceClient.NlpResponse("fear", 0.7, -0.7, 0.6);
        double score = scorer.score(fear);
        assertTrue(score > 20.0, "Fear should produce elevated score, got: " + score);
    }

    @Test
    @DisplayName("Anger emotion → moderate-to-high score")
    void angerEmotion_moderateScore() {
        NlpServiceClient.NlpResponse anger =
                new NlpServiceClient.NlpResponse("anger", 0.8, -0.9, 0.9);
        double score = scorer.score(anger);
        assertTrue(score > 20.0, "Anger with negative valence should produce moderate-high score, got: " + score);
    }

    @Test
    @DisplayName("Positive emotion with positive valence → score decreases vs neutral")
    void positiveVsNeutral_lowerScore() {
        NlpServiceClient.NlpResponse positive =
                new NlpServiceClient.NlpResponse("joy", 0.9, 0.9, 0.5);
        NlpServiceClient.NlpResponse neutral =
                new NlpServiceClient.NlpResponse("neutral", 0.65, 0.0, 0.0);

        double positiveScore = scorer.score(positive);
        double neutralScore = scorer.score(neutral);

        assertTrue(positiveScore < neutralScore,
                "Positive emotion score (" + positiveScore + ") should be lower than neutral (" + neutralScore + ")");
    }

    @Test
    @DisplayName("Score is clamped to [0, 40]")
    void score_neverExceedsMax() {
        // Grief with extreme negative valence and high confidence
        NlpServiceClient.NlpResponse extreme =
                new NlpServiceClient.NlpResponse("grief", 1.0, -1.0, 1.0);
        double score = scorer.score(extreme);
        assertTrue(score <= 40.0, "Score must never exceed 40, got: " + score);
        assertTrue(score >= 0.0, "Score must never be negative, got: " + score);
    }

    @Test
    @DisplayName("Unknown emotion uses default weight")
    void unknownEmotion_usesDefault() {
        NlpServiceClient.NlpResponse unknown =
                new NlpServiceClient.NlpResponse("some_unknown_emotion", 0.5, -0.5, 0.3);
        double score = scorer.score(unknown);
        assertTrue(score > 0, "Unknown emotion should still produce a score using default weight");
    }
}
