package dev.xj16.beacon.anomaly;

import dev.xj16.beacon.common.EnrichedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticalScorerTest {

    private final StatisticalScorer scorer = new StatisticalScorer();

    private EnrichedEvent event(String level, int severity, String message, Map<String, Object> attrs) {
        return new EnrichedEvent(
                "id", "svc", level, severity, message, Instant.now(), Instant.now(),
                "host", null, "prod", attrs, null);
    }

    @Test
    void fatalEventWithKeywordAndErrorStatusScoresHigh() {
        EnrichedEvent e = event("FATAL", 5, "OutOfMemory: heap exhausted",
                Map.of("status", 500, "latency_ms", 8000));
        double score = scorer.score(e);
        assertTrue(score >= 0.9, "expected high score, got " + score);
        assertTrue(score <= 1.0);
    }

    @Test
    void benignInfoEventScoresLow() {
        EnrichedEvent e = event("INFO", 2, "request completed", Map.of("status", 200));
        double score = scorer.score(e);
        assertTrue(score < 0.3, "expected low score, got " + score);
    }

    @Test
    void scoreIsAlwaysClamped() {
        assertEquals(1.0, StatisticalScorer.clamp(5.0));
        assertEquals(0.0, StatisticalScorer.clamp(-1.0));
        assertEquals(0.5, StatisticalScorer.clamp(0.5));
    }

    @Test
    void latencyTiersAffectScore() {
        EnrichedEvent slow = event("WARN", 3, "slow query", Map.of("latency_ms", 6000));
        EnrichedEvent fast = event("WARN", 3, "slow query", Map.of("latency_ms", 50));
        assertTrue(scorer.score(slow) > scorer.score(fast));
    }
}
