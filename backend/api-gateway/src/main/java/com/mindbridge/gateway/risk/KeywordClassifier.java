package com.mindbridge.gateway.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Crisis keyword classifier for mental health risk scoring.
 *
 * <p>Maintains a weighted crisis vocabulary list covering suicidal ideation,
 * self-harm, hopelessness, and related distress signals. Each keyword or
 * phrase is assigned a weight (1.0–3.0) reflecting its clinical severity.</p>
 *
 * <p>The classifier performs case-insensitive matching and handles common
 * misspellings and abbreviations (e.g. "su1cide", "kms", "wanna die").</p>
 *
 * <p>Score contribution: weighted hit count normalised to a 0–40 sub-score.</p>
 */
@Component
public class KeywordClassifier {

    private static final Logger logger = LoggerFactory.getLogger(KeywordClassifier.class);

    /** Maximum sub-score this classifier can produce. */
    private static final double MAX_SUB_SCORE = 40.0;

    /**
     * Normalisation ceiling — if the raw weighted hit count reaches this
     * value the sub-score is clamped to {@link #MAX_SUB_SCORE}.
     */
    private static final double NORMALISATION_CEILING = 10.0;

    /**
     * Ordered map of crisis patterns → weight.
     * Patterns are compiled once at construction time for performance.
     * Ordered from highest severity (weight 3.0) to lowest (weight 0.5).
     */
    private static final Map<Pattern, Double> CRISIS_VOCABULARY = new LinkedHashMap<>();

    static {
        // === Suicidal ideation — weight 3.0 ===
        addPhrase("kill myself",           3.0);
        addPhrase("end my life",           3.0);
        addPhrase("want to die",           3.0);
        addPhrase("wanna die",             3.0);
        addPhrase("suicide",               3.0);
        addPhrase("su[i1]c[i1]de",         3.0);  // common leet-speak
        addPhrase("kms",                   3.0);  // "kill myself" abbreviation
        addPhrase("don'?t want to live",   3.0);
        addPhrase("no reason to live",     3.0);
        addPhrase("better off dead",       3.0);
        addPhrase("wish i was dead",       3.0);
        addPhrase("wish i were dead",      3.0);
        addPhrase("take my own life",      3.0);
        addPhrase("suicidal",              3.0);

        // === Self-harm — weight 2.5 ===
        addPhrase("self[- ]?harm",         2.5);
        addPhrase("cut myself",            2.5);
        addPhrase("cutting myself",        2.5);
        addPhrase("hurt myself",           2.5);
        addPhrase("hurting myself",        2.5);
        addPhrase("overdose",              2.5);

        // === Severe hopelessness — weight 2.0 ===
        addPhrase("hopeless",              2.0);
        addPhrase("no hope",              2.0);
        addPhrase("no way out",            2.0);
        addPhrase("can'?t go on",          2.0);
        addPhrase("can'?t take it",        2.0);
        addPhrase("give up",              2.0);
        addPhrase("giving up",            2.0);
        addPhrase("worthless",             2.0);
        addPhrase("i am a burden",         2.0);
        addPhrase("i'?m a burden",         2.0);
        addPhrase("nobody cares",          2.0);
        addPhrase("no one cares",          2.0);

        // === Moderate distress — weight 1.5 ===
        addPhrase("i can'?t cope",         1.5);
        addPhrase("overwhelming",          1.5);
        addPhrase("i hate myself",         1.5);
        addPhrase("hate my life",          1.5);
        addPhrase("trapped",               1.5);
        addPhrase("desperate",             1.5);
        addPhrase("despair",               1.5);
        addPhrase("empty inside",          1.5);
        addPhrase("numb",                  1.5);

        // === Low distress signals — weight 1.0 ===
        addPhrase("lonely",                1.0);
        addPhrase("alone",                 1.0);
        addPhrase("isolated",              1.0);
        addPhrase("exhausted",             1.0);
        addPhrase("broken",                1.0);
        addPhrase("crying",                1.0);
        addPhrase("panic",                 1.0);
        addPhrase("terrified",             1.0);
        addPhrase("scared",                1.0);

        // === Mild markers — weight 0.5 ===
        addPhrase("anxious",               0.5);
        addPhrase("stressed",              0.5);
        addPhrase("depressed",             0.5);
        addPhrase("sad",                   0.5);
        addPhrase("angry",                 0.5);
        addPhrase("frustrated",            0.5);
    }

    /**
     * Compile a case-insensitive word-boundary pattern and register it
     * with the given weight.
     *
     * @param phrase regex fragment (will be wrapped with word boundaries)
     * @param weight severity weight (higher = more critical)
     */
    private static void addPhrase(String phrase, double weight) {
        // Use word boundaries for single words, lookahead/behind for multi-word
        String regex = phrase.contains(" ")
                ? "(?i)" + phrase
                : "(?i)\\b" + phrase + "\\b";
        CRISIS_VOCABULARY.put(Pattern.compile(regex), weight);
    }

    /**
     * Score a message against the crisis vocabulary.
     *
     * <p>The raw weighted hit count is normalised to a 0–40 sub-score
     * using the formula: {@code min(rawWeightedHits / CEILING, 1.0) × 40}.</p>
     *
     * @param message the raw message text
     * @return a sub-score in the range [0, 40]
     */
    public double score(String message) {
        if (message == null || message.isBlank()) {
            return 0.0;
        }

        String lowerMessage = message.toLowerCase();
        double rawWeight = 0.0;

        for (Map.Entry<Pattern, Double> entry : CRISIS_VOCABULARY.entrySet()) {
            if (entry.getKey().matcher(lowerMessage).find()) {
                rawWeight += entry.getValue();
                logger.debug("Crisis keyword match: pattern={}, weight={}",
                        entry.getKey().pattern(), entry.getValue());
            }
        }

        // Normalise to 0–40
        double normalised = Math.min(rawWeight / NORMALISATION_CEILING, 1.0) * MAX_SUB_SCORE;

        logger.debug("KeywordClassifier: rawWeight={}, normalised={}", rawWeight, normalised);
        return normalised;
    }
}
