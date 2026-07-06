package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnrichmentServiceTest {

    private final EnrichmentService service = new EnrichmentService();

    @Test
    void enrichesSeverityAndEnvironment() {
        LogEvent raw = new LogEvent(
                "evt-1", "checkout-api-prod", "ERROR", "payment failed",
                Instant.parse("2026-01-01T00:00:00Z"), "pod-prod-7", "trace-abc",
                Map.of("status", 500));

        EnrichedEvent enriched = service.enrich(raw);

        assertEquals("evt-1", enriched.id());
        assertEquals(4, enriched.severity());       // ERROR -> 4
        assertEquals("prod", enriched.environment());
        assertEquals(500, enriched.attributes().get("status"));
        assertNotNull(enriched.ingestedAt());
        assertNull(enriched.anomalyScore());
    }

    @Test
    void backfillsMissingIdAndTimestamp() {
        LogEvent raw = new LogEvent(
                null, "svc-staging", "info", "hello", null, "host", null, Map.of());

        EnrichedEvent enriched = service.enrich(raw);

        assertNotNull(enriched.id());
        assertNotNull(enriched.timestamp());
        assertEquals("staging", enriched.environment());
        assertEquals(2, enriched.severity());       // INFO -> 2
    }

    @Test
    void unknownEnvironmentWhenNoHint() {
        LogEvent raw = new LogEvent(
                "id", "mysterious", "WARN", "m", Instant.now(), "boxA", null, Map.of());
        assertEquals("unknown", service.enrich(raw).environment());
    }

    @Test
    void anomalyScoreCanBeAttached() {
        EnrichedEvent enriched = service.enrich(
                new LogEvent("id", "svc-dev", "INFO", "m", Instant.now(), "h", null, Map.of()));
        EnrichedEvent scored = enriched.withAnomalyScore(0.9);
        assertEquals(0.9, scored.anomalyScore());
        assertEquals("dev", scored.environment());
    }
}
