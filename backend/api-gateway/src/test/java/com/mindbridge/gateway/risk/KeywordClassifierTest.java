package com.mindbridge.gateway.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KeywordClassifier}.
 *
 * <p>Validates crisis vocabulary detection, case-insensitivity,
 * misspelling handling, and score normalisation.</p>
 */
class KeywordClassifierTest {

    private final KeywordClassifier classifier = new KeywordClassifier();

    @Test
    @DisplayName("Null or blank message → score is 0")
    void nullOrBlankMessage_returnsZero() {
        assertEquals(0.0, classifier.score(null));
        assertEquals(0.0, classifier.score(""));
        assertEquals(0.0, classifier.score("   "));
    }

    @Test
    @DisplayName("Message with no crisis keywords → score is 0")
    void noCrisisKeywords_returnsZero() {
        assertEquals(0.0, classifier.score("I had a great day at the park"));
        assertEquals(0.0, classifier.score("The weather is nice today"));
    }

    @Test
    @DisplayName("Single suicidal keyword → high score")
    void suicidalKeyword_highScore() {
        double score = classifier.score("I want to commit suicide");
        assertTrue(score > 0, "Score should be positive for suicidal keyword");
        assertTrue(score >= 12.0, "Suicidal keyword (weight 3.0) should produce substantial score");
    }

    @Test
    @DisplayName("Multiple severe keywords → score approaches maximum")
    void multipleSevereKeywords_approachesMax() {
        double score = classifier.score("I want to kill myself, I feel hopeless, there's no way out and I want to die");
        assertTrue(score >= 30.0, "Multiple severe keywords should produce score near maximum");
    }

    @Test
    @DisplayName("Case-insensitive matching works")
    void caseInsensitive_detectsKeywords() {
        double lower = classifier.score("suicide");
        double upper = classifier.score("SUICIDE");
        double mixed = classifier.score("SuIcIdE");
        assertEquals(lower, upper, 0.01, "Case should not affect score");
        assertEquals(lower, mixed, 0.01, "Mixed case should produce same score");
    }

    @Test
    @DisplayName("Common misspelling su1cide is detected")
    void leetSpeak_su1cide_detected() {
        double score = classifier.score("su1cide");
        assertTrue(score > 0, "Leet-speak 'su1cide' should be detected");
    }

    @Test
    @DisplayName("Abbreviation kms is detected")
    void abbreviation_kms_detected() {
        double score = classifier.score("honestly kms");
        assertTrue(score > 0, "Abbreviation 'kms' should be detected");
    }

    @Test
    @DisplayName("Self-harm keywords produce moderate score")
    void selfHarmKeywords_moderateScore() {
        double score = classifier.score("I've been cutting myself");
        assertTrue(score > 0 && score <= 40, "Self-harm should produce moderate score");
    }

    @Test
    @DisplayName("Mild distress markers produce low score")
    void mildDistress_lowScore() {
        double score = classifier.score("I feel a bit sad today");
        assertTrue(score > 0, "Mild distress should produce some score");
        assertTrue(score < 10, "Mild distress alone should not produce high score");
    }

    @Test
    @DisplayName("Positive message → score is 0")
    void positiveMessage_returnsZero() {
        double score = classifier.score("I am feeling happy and grateful for everything");
        assertEquals(0.0, score, 0.01, "Purely positive message should score 0");
    }

    @Test
    @DisplayName("Score is clamped to [0, 40]")
    void score_clampedToMax() {
        // Lots of crisis keywords to try to exceed max
        double score = classifier.score(
            "I want to kill myself and end my life, suicide is the only option, " +
            "I want to die, no hope, hopeless, worthless, self-harm, " +
            "cutting myself, overdose, can't go on, no way out, " +
            "better off dead, no reason to live");
        assertTrue(score <= 40.0, "Score must never exceed 40");
        assertTrue(score >= 35.0, "Extreme message should be near maximum");
    }
}
