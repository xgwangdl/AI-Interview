package com.guangge.Interview.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeliveryMetricsCalculatorTest {

    private final DeliveryMetricsCalculator calculator = new DeliveryMetricsCalculator();

    @Test
    void calculatesSpeechRateFromTheTranscriptAndDuration() {
        EvaluationResult.DeliveryMetrics metrics = calculator.calculate("你好世界", 2);

        assertEquals(120.0, metrics.speechRateCharactersPerMinute(), 0.001);
    }

    @Test
    void countsFillerWordsWithoutAskingTheModelToGuess() {
        EvaluationResult.DeliveryMetrics metrics = calculator.calculate(
                "嗯，我觉得这个方案，嗯，还需要再考虑一下。", 30);

        assertEquals(2, metrics.fillerWordCount());
    }

    @Test
    void leavesPauseMetricsUnknownWhenThereAreNoAsrTimestamps() {
        EvaluationResult.DeliveryMetrics metrics = calculator.calculate("我负责订单系统重构", 20);

        assertNull(metrics.pauseCount());
        assertNull(metrics.totalPauseSeconds());
    }

    @Test
    void leavesSpeechRateUnknownWhenDurationIsUnavailable() {
        EvaluationResult.DeliveryMetrics metrics = calculator.calculate("我负责订单系统重构", null);

        assertNull(metrics.speechRateCharactersPerMinute());
        assertEquals(0, metrics.fillerWordCount());
    }
}
