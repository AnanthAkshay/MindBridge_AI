package com.mindbridge.gateway.risk;

import com.mindbridge.core.entity.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Weighted risk aggregator that combines the three sub-scores
 * (keyword, tonal, baseline) into a single 0–100 risk score.
 *
 * <p>Default weights (configurable via application.yml or environment):</p>
 * <ul>
 *   <li>Keyword classifier: 40%</li>
 *   <li>Tonal emotion scorer: 40%</li>
 *   <li>Session baseline adjuster: 20%</li>
 * </ul>
 *
 * <p>The final score is clamped to [0, 100] and mapped to a {@link RiskLevel}.</p>
 */
@Component
public class WeightedRiskAggregator {

    private static final Logger logger = LoggerFactory.getLogger(WeightedRiskAggregator.class);

    private final double keywordWeight;
    private final double tonalWeight;
    private final double baselineWeight;

    /**
     * Construct the aggregator with configurable weights.
     *
     * @param keywordWeight  weight for the keyword classifier sub-score (default 0.4)
     * @param tonalWeight    weight for the tonal emotion sub-score (default 0.4)
     * @param baselineWeight weight for the session baseline modifier (default 0.2)
     */
    public WeightedRiskAggregator(
            @Value("${mindbridge.risk.weight.keyword:0.4}") double keywordWeight,
            @Value("${mindbridge.risk.weight.tonal:0.4}") double tonalWeight,
            @Value("${mindbridge.risk.weight.baseline:0.2}") double baselineWeight) {
        this.keywordWeight = keywordWeight;
        this.tonalWeight = tonalWeight;
        this.baselineWeight = baselineWeight;
    }

    /**
     * Aggregate three sub-scores into a final risk result.
     *
     * <p>Formula: {@code clamp(keywordScore × kW + tonalScore × tW + baselineScore × bW, 0, 100)}</p>
     *
     * <p>Sub-score ranges:</p>
     * <ul>
     *   <li>keywordScore: [0, 40]</li>
     *   <li>tonalScore: [0, 40]</li>
     *   <li>baselineScore: [0, 20]</li>
     * </ul>
     *
     * <p>With default weights (0.4, 0.4, 0.2), the theoretical max is:
     * {@code 40×0.4 + 40×0.4 + 20×0.2 = 16 + 16 + 4 = 36}. To scale this to the
     * full 0–100 range, the sub-scores are normalised to their respective maximums
     * before weighting, then multiplied by 100.</p>
     *
     * @param keywordScore  raw sub-score from {@link KeywordClassifier} [0, 40]
     * @param tonalScore    raw sub-score from {@link TonalEmotionScorer} [0, 40]
     * @param baselineScore raw sub-score from {@link SessionBaselineAdjuster} [0, 20]
     * @return the aggregated {@link RiskResult}
     */
    public RiskResult aggregate(double keywordScore, double tonalScore, double baselineScore) {
        // Normalise each sub-score to [0, 1]
        double normKeyword  = Math.min(keywordScore  / 40.0, 1.0);
        double normTonal    = Math.min(tonalScore    / 40.0, 1.0);
        double normBaseline = Math.min(baselineScore / 20.0, 1.0);

        // Weighted combination → [0, 1]
        double weightedSum = (normKeyword * keywordWeight)
                           + (normTonal   * tonalWeight)
                           + (normBaseline * baselineWeight);

        // Scale to 0–100 and clamp
        int score = (int) Math.round(Math.max(0.0, Math.min(weightedSum * 100.0, 100.0)));

        RiskLevel level = RiskLevel.fromScore(score);

        logger.info("RiskAggregator: keyword={} (norm={}), tonal={} (norm={}), baseline={} (norm={}), " +
                    "weighted={}, finalScore={}, level={}",
                keywordScore, normKeyword, tonalScore, normTonal, baselineScore, normBaseline,
                weightedSum, score, level);

        return new RiskResult(score, level);
    }

    /**
     * Immutable result of risk aggregation.
     *
     * @param score the final risk score (0–100)
     * @param level the classified risk level
     */
    public record RiskResult(int score, RiskLevel level) {}
}
