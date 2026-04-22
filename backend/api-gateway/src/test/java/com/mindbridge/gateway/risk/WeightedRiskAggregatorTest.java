package com.mindbridge.gateway.risk;

import com.mindbridge.core.entity.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WeightedRiskAggregator}.
 *
 * <p>Validates weighted combination, normalisation, clamping,
 * and risk level classification.</p>
 */
class WeightedRiskAggregatorTest {

    /** Default weights: keyword=0.4, tonal=0.4, baseline=0.2 */
    private final WeightedRiskAggregator aggregator = new WeightedRiskAggregator(0.4, 0.4, 0.2);

    @Test
    @DisplayName("All sub-scores at zero → score=0, level=LOW")
    void allZero_returnsLow() {
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(0, 0, 0);
        assertEquals(0, result.score());
        assertEquals(RiskLevel.LOW, result.level());
    }

    @Test
    @DisplayName("All sub-scores at maximum → score=100, level=HIGH")
    void allMax_returnsHigh() {
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(40, 40, 20);
        assertEquals(100, result.score());
        assertEquals(RiskLevel.HIGH, result.level());
    }

    @Test
    @DisplayName("Moderate keyword and tonal → MODERATE level")
    void moderateScores_moderateLevel() {
        // keyword=20/40=0.5, tonal=20/40=0.5, baseline=0/20=0
        // weighted = 0.5*0.4 + 0.5*0.4 + 0*0.2 = 0.4 → 40
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(20, 20, 0);
        assertEquals(40, result.score());
        assertEquals(RiskLevel.MODERATE, result.level());
    }

    @Test
    @DisplayName("HIGH boundary at score 65")
    void highBoundary() {
        // Need weighted = 0.65
        // keyword=40/40=1.0, tonal=25/40=0.625, baseline=0/20=0
        // weighted = 1.0*0.4 + 0.625*0.4 + 0*0.2 = 0.4 + 0.25 = 0.65 → 65
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(40, 25, 0);
        assertEquals(65, result.score());
        assertEquals(RiskLevel.HIGH, result.level());
    }

    @Test
    @DisplayName("MODERATE boundary at score 40")
    void moderateBoundary() {
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(20, 20, 0);
        assertEquals(40, result.score());
        assertEquals(RiskLevel.MODERATE, result.level());
    }

    @Test
    @DisplayName("LOW boundary at score 39")
    void lowBoundary() {
        // Need weighted ≈ 0.39
        // keyword=19.5/40=0.4875, tonal=19.5/40=0.4875, baseline=0
        // weighted = 0.4875*0.4 + 0.4875*0.4 = 0.39 → 39
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(19.5, 19.5, 0);
        assertEquals(39, result.score());
        assertEquals(RiskLevel.LOW, result.level());
    }

    @Test
    @DisplayName("Baseline modifier increases final score")
    void baselineModifier_increasesScore() {
        WeightedRiskAggregator.RiskResult withoutBaseline = aggregator.aggregate(20, 20, 0);
        WeightedRiskAggregator.RiskResult withBaseline = aggregator.aggregate(20, 20, 20);

        assertTrue(withBaseline.score() > withoutBaseline.score(),
                "Adding baseline should increase score");
    }

    @Test
    @DisplayName("Custom weights are respected")
    void customWeights_respected() {
        WeightedRiskAggregator customAggregator = new WeightedRiskAggregator(0.6, 0.3, 0.1);
        // keyword=40, tonal=0, baseline=0
        // weighted = 1.0 * 0.6 = 0.6 → 60
        WeightedRiskAggregator.RiskResult result = customAggregator.aggregate(40, 0, 0);
        assertEquals(60, result.score());
    }

    @Test
    @DisplayName("Score never exceeds 100")
    void score_neverExceeds100() {
        // Even with sub-scores beyond normal max
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(50, 50, 30);
        assertTrue(result.score() <= 100, "Score must not exceed 100");
    }

    @Test
    @DisplayName("Score never goes below 0")
    void score_neverBelowZero() {
        WeightedRiskAggregator.RiskResult result = aggregator.aggregate(-5, -5, -5);
        assertTrue(result.score() >= 0, "Score must not go below 0");
    }
}
