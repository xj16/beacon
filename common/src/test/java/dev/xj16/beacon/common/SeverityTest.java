package dev.xj16.beacon.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeverityTest {

    @Test
    void mapsKnownLevels() {
        assertEquals(1, Severity.toNumeric("debug"));
        assertEquals(3, Severity.toNumeric("WARN"));
        assertEquals(5, Severity.toNumeric("Fatal"));
    }

    @Test
    void unknownLevelDefaultsToInfo() {
        assertEquals(2, Severity.toNumeric("nonsense"));
        assertEquals(2, Severity.toNumeric(null));
        assertEquals(2, Severity.toNumeric("   "));
    }

    @Test
    void logEventErrorDetection() {
        LogEvent err = new LogEvent("1", "svc", "ERROR", "boom",
                Instant.now(), "host", null, Map.of());
        LogEvent info = new LogEvent("2", "svc", "INFO", "ok",
                Instant.now(), "host", null, Map.of());
        assertTrue(err.isError());
        assertFalse(info.isError());
    }

    @Test
    void nullAttributesBecomeEmptyMap() {
        LogEvent e = new LogEvent("1", "svc", "INFO", "m",
                Instant.now(), "host", null, null);
        assertTrue(e.attributes().isEmpty());
    }
}
