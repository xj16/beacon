package dev.xj16.beacon.anomaly;

import dev.xj16.beacon.common.EnrichedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaClientTest {

    @Test
    void parsesPlainNumber() {
        assertEquals(Optional.of(0.8), OllamaClient.parseScore("0.8"));
        assertEquals(Optional.of(1.0), OllamaClient.parseScore("1.0"));
        assertEquals(Optional.of(0.0), OllamaClient.parseScore("0"));
    }

    @Test
    void parsesNumberFromChattyReply() {
        assertEquals(Optional.of(0.7),
                OllamaClient.parseScore("I would rate this event 0.7 on the scale."));
    }

    @Test
    void rejectsOutOfRangeAndGarbage() {
        assertTrue(OllamaClient.parseScore("").isEmpty());
        assertTrue(OllamaClient.parseScore(null).isEmpty());
        // 5.0 is out of range; regex won't match a valid [0,1] token here
        assertTrue(OllamaClient.parseScore("banana").isEmpty());
    }

    @Test
    void promptIncludesEventDetails() {
        EnrichedEvent e = new EnrichedEvent(
                "id", "checkout-api", "ERROR", 4, "payment failed",
                Instant.now(), Instant.now(), "host", null, "prod",
                Map.of("status", 500), null);
        String prompt = OllamaClient.buildPrompt(e);
        assertTrue(prompt.contains("checkout-api"));
        assertTrue(prompt.contains("payment failed"));
        assertTrue(prompt.contains("ERROR"));
    }

    @Test
    void unavailableWhenNoServer() {
        // Point at a port nothing is listening on; must not throw and must report unavailable.
        OllamaClient client = new OllamaClient("http://localhost:1", "llama3.2", 300);
        assertFalse(client.isAvailable());
        EnrichedEvent e = new EnrichedEvent(
                "id", "svc", "INFO", 2, "ok", Instant.now(), Instant.now(),
                "host", null, "dev", Map.of(), null);
        assertTrue(client.score(e).isEmpty());
    }
}
