package dev.xj16.beacon.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.xj16.beacon.common.LogEvent;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that a {@link LogEvent} (which carries an {@link Instant}) survives a JSON
 * serialize -> deserialize round-trip through the exact serializer/deserializer configuration the
 * Kafka factories use. This is what guards against the "Instant not supported" deserialization
 * failure that would otherwise silently drop events on the consumer.
 */
class LogEventSerdeTest {

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void roundTripsLogEventWithInstant() {
        LogEvent original = new LogEvent(
                "evt-1", "orders-api-prod", "ERROR", "checkout exploded",
                Instant.parse("2026-02-02T10:00:00Z"), "pod-prod-3", "trace-xyz",
                Map.of("status", 500, "latency_ms", 1200));

        Serializer<LogEvent> serializer = new JsonSerializer<>(mapper());
        JsonDeserializer<LogEvent> jsonDeserializer = new JsonDeserializer<>(LogEvent.class, mapper());
        jsonDeserializer.addTrustedPackages("dev.xj16.beacon.common");
        jsonDeserializer.setUseTypeHeaders(false);
        Deserializer<LogEvent> deserializer = jsonDeserializer;

        byte[] bytes = serializer.serialize("beacon.events", original);
        assertNotNull(bytes);

        LogEvent restored = deserializer.deserialize("beacon.events", bytes);
        assertNotNull(restored, "deserialized event must not be null");
        assertEquals("evt-1", restored.id());
        assertEquals(Instant.parse("2026-02-02T10:00:00Z"), restored.timestamp());
        assertEquals("orders-api-prod", restored.service());
        assertEquals(500, restored.attributes().get("status"));

        serializer.close();
        deserializer.close();
    }
}
