package com.mindbridge.gateway.risk;

import com.mindbridge.gateway.chat.NlpServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tonal emotion scorer that consumes NLP pipeline output (Step 4)
 * and maps emotion labels to a 0–40 risk sub-score.
 *
 * <p>High-distress emotions (sadness, fear, anger, disgust, grief, anxious)
 * contribute heavily to the sub-score, while neutral or positive emotions
 * (joy, love, gratitude, excitement, pride, relief, optimism) produce low
 * or zero contributions.</p>
 *
 * <p>The score combines two signals:</p>
 * <ol>
 *   <li><strong>Emotion weight</strong> — a fixed coefficient per emotion label</li>
 *   <li><strong>Valence penalty</strong> — negative valence amplifies the score</li>
 * </ol>
 */
@Component
public class TonalEmotionScorer {

    private static final Logger logger = LoggerFactory.getLogger(TonalEmotionScorer.class);

    /** Maximum sub-score this scorer can produce. */
    private static final double MAX_SUB_SCORE = 40.0;

    /**
     * Emotion label → base risk weight (0.0–1.0).
     * A weight of 1.0 means the emotion alone can push this sub-score to maximum.
     */
    private static final Map<String, Double> EMOTION_WEIGHTS = Map.ofEntries(
            // High-distress emotions
            Map.entry("grief",          1.00),
            Map.entry("sadness",        0.90),
            Map.entry("fear",           0.85),
            Map.entry("anxious",        0.80),
            Map.entry("nervousness",    0.75),
            Map.entry("anger",          0.70),
            Map.entry("disgust",        0.65),
            Map.entry("remorse",        0.60),
            Map.entry("disappointment", 0.55),
            Map.entry("disapproval",    0.45),
            Map.entry("annoyance",      0.35),
            Map.entry("embarrassment",  0.40),
            Map.entry("tired",          0.40),

            // Neutral / mild
            Map.entry("confusion",      0.25),
            Map.entry("surprise",       0.15),
            Map.entry("realization",    0.10),
            Map.entry("neutral",        0.05),
            Map.entry("curiosity",      0.05),

            // Positive — very low or zero contribution
            Map.entry("approval",       0.03),
            Map.entry("caring",         0.03),
            Map.entry("desire",         0.03),
            Map.entry("amusement",      0.02),
            Map.entry("admiration",     0.02),
            Map.entry("optimism",       0.02),
            Map.entry("pride",          0.01),
            Map.entry("relief",         0.01),
            Map.entry("excitement",     0.01),
            Map.entry("gratitude",      0.01),
            Map.entry("love",           0.01),
            Map.entry("joy",            0.00)
    );

    /**
     * Score the NLP pipeline output for tonal risk.
     *
     * <p>Formula: {@code clamp(emotionWeight × confidenceBoost × valencePenalty × MAX, 0, MAX)}</p>
     *
     * @param nlpResponse the response from the NLP service (Step 4)
     * @return a sub-score in the range [0, 40]
     */
    public double score(NlpServiceClient.NlpResponse nlpResponse) {
        if (nlpResponse == null) {
            return 0.0;
        }

        String emotion = nlpResponse.emotion() != null
                ? nlpResponse.emotion().toLowerCase()
                : "neutral";

        double confidence = nlpResponse.confidence() != null
                ? nlpResponse.confidence()
                : 0.0;

        double valence = nlpResponse.valence() != null
                ? nlpResponse.valence()
                : 0.0;

        // 1. Look up base emotion weight
        double emotionWeight = EMOTION_WEIGHTS.getOrDefault(emotion, 0.20);

        // 2. Confidence boost — higher confidence in a distress emotion amplifies the score
        //    Range: 0.8 (low confidence) to 1.2 (high confidence)
        double confidenceBoost = 0.8 + (confidence * 0.4);

        // 3. Valence penalty — negative valence pushes the score upward
        //    valence ∈ [-1, 1] → penalty ∈ [1.0, 1.5] for negative, [0.5, 1.0] for positive
        double valencePenalty = 1.0 + (-valence * 0.5);

        // 4. Combine and clamp
        double raw = emotionWeight * confidenceBoost * valencePenalty * MAX_SUB_SCORE;
        double clamped = Math.max(0.0, Math.min(raw, MAX_SUB_SCORE));

        logger.debug("TonalEmotionScorer: emotion={}, weight={}, confidence={}, valence={}, " +
                     "confidenceBoost={}, valencePenalty={}, raw={}, clamped={}",
                emotion, emotionWeight, confidence, valence,
                confidenceBoost, valencePenalty, raw, clamped);

        return clamped;
    }
}
