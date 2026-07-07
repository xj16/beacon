package dev.xj16.beacon.anomaly.alert;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the rule-matching predicate at the heart of the alerting engine. No Spring,
 * no database — just the score/severity/service breach logic.
 */
class AlertRuleTest {

    private AlertRule rule(String service, double minScore, int minSeverity, boolean enabled) {
        AlertRule r = new AlertRule();
        set(r, "service", service);
        set(r, "minAnomalyScore", minScore);
        set(r, "minSeverity", minSeverity);
        set(r, "enabled", enabled);
        return r;
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void breachesWhenScoreAndSeverityBothMeetThresholds() {
        AlertRule r = rule("orders-api-prod", 0.85, 4, true);
        assertTrue(r.matches("orders-api-prod", 0.9, 5));
        assertTrue(r.matches("orders-api-prod", 0.85, 4)); // boundary is inclusive
    }

    @Test
    void doesNotBreachWhenScoreBelowThreshold() {
        AlertRule r = rule("orders-api-prod", 0.85, 4, true);
        assertFalse(r.matches("orders-api-prod", 0.84, 5));
    }

    @Test
    void doesNotBreachWhenSeverityBelowThreshold() {
        AlertRule r = rule("orders-api-prod", 0.85, 4, true);
        assertFalse(r.matches("orders-api-prod", 0.99, 3));
    }

    @Test
    void serviceScopedRuleIgnoresOtherServices() {
        AlertRule r = rule("orders-api-prod", 0.5, 1, true);
        assertFalse(r.matches("some-other-service", 1.0, 5));
    }

    @Test
    void catchAllRuleWithNullServiceMatchesAnyService() {
        AlertRule r = rule(null, 0.9, 4, true);
        assertTrue(r.matches("brand-new-service", 0.95, 4));
    }

    @Test
    void disabledRuleNeverMatches() {
        AlertRule r = rule("orders-api-prod", 0.0, 1, false);
        assertFalse(r.matches("orders-api-prod", 1.0, 5));
    }
}
