package dev.xj16.beacon.anomaly.alert;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.xj16.beacon.common.EnrichedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flagship test for the alerting engine — the subsystem the README's architecture diagram promises
 * and that previously did not exist. It boots the full Spring context against the default in-memory
 * H2 (so {@code schema.sql} + {@code data.sql} run, seeding real rules), and exercises the whole
 * detect→persist→notify→read-back path. Elasticsearch is mocked out so this needs no containers and
 * runs in the fast unit-test lane.
 */
@SpringBootTest(properties = "beacon.ollama.base-url=http://localhost:1")
class AlertServiceTest {

    @MockBean
    ElasticsearchClient elasticsearchClient;

    @Autowired
    AlertService alertService;

    @Autowired
    AlertRepository alertRepository;

    @Autowired
    AlertRuleRepository ruleRepository;

    private EnrichedEvent event(String id, String service, int severity, String message) {
        return new EnrichedEvent(
                id, service, "FATAL", severity, message,
                Instant.now(), Instant.now(), "host", null, "prod",
                Map.of("status", 500), null);
    }

    @Test
    void seedRulesAreLoaded() {
        // schema.sql + data.sql seeded the per-service rule and the catch-all default.
        assertTrue(ruleRepository.findByEnabledTrue().size() >= 2);
    }

    @Test
    void highScoreOnConfiguredServiceFiresAndPersistsAlert() {
        EnrichedEvent e = event("alert-evt-1", "orders-api-prod", 5, "OutOfMemory killed the pod");

        Optional<Alert> fired = alertService.evaluate(e, 0.97);

        assertTrue(fired.isPresent(), "a breaching event should fire an alert");
        Alert alert = fired.get();
        assertEquals("orders-api-prod", alert.getService());
        assertEquals("checkout", alert.getTeam(), "team should be resolved from the service catalog");
        assertEquals(5, alert.getSeverity());
        assertTrue(alert.isNotified(), "logging notifier always delivers");

        // It is durably persisted and surfaced newest-first via recentAlerts().
        List<Alert> recent = alertService.recentAlerts(10);
        assertTrue(recent.stream().anyMatch(a -> "alert-evt-1".equals(a.getEventId())));
    }

    @Test
    void benignEventDoesNotFire() {
        EnrichedEvent e = event("alert-evt-2", "orders-api-prod", 2, "all good");
        assertFalse(alertService.evaluate(e, 0.1).isPresent());
    }

    @Test
    void lowSeverityNeverFiresEvenWithHighScore() {
        // Severity 3 is below every seeded rule's min_severity of 4.
        EnrichedEvent e = event("alert-evt-3", "orders-api-prod", 3, "weird but low severity");
        assertFalse(alertService.evaluate(e, 1.0).isPresent());
    }

    @Test
    void unlistedServiceStillHitsCatchAllRule() {
        EnrichedEvent e = event("alert-evt-4", "mystery-service", 5, "panic in an unknown service");
        Optional<Alert> fired = alertService.evaluate(e, 0.95);
        assertTrue(fired.isPresent(), "catch-all rule (service NULL) should cover unlisted services");
    }

    @Test
    void alertingIsIdempotentPerEventId() {
        EnrichedEvent e = event("alert-evt-5", "orders-api-prod", 5, "timeout storm");
        assertTrue(alertService.evaluate(e, 0.95).isPresent());
        // Re-scoring the same event must not double-fire.
        assertFalse(alertService.evaluate(e, 0.95).isPresent());
        long count = alertRepository.findAll().stream()
                .filter(a -> "alert-evt-5".equals(a.getEventId()))
                .count();
        assertEquals(1, count);
    }
}
