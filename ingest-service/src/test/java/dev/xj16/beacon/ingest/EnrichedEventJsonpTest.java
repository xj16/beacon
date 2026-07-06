package dev.xj16.beacon.ingest;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.xj16.beacon.common.EnrichedEvent;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies an {@link EnrichedEvent} serializes cleanly through the same Elasticsearch JSONP mapper
 * the indexer uses. This guards the app-side write path: if the mapper cannot serialize the
 * document (e.g. a missing java.time module), the Kafka listener's index() call would throw and no
 * event would ever be persisted.
 */
class EnrichedEventJsonpTest {

    @Test
    void enrichedEventSerializesThroughElasticsearchMapper() {
        // Mirror the app's Elasticsearch mapper configuration (see ElasticsearchConfig).
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);

        EnrichedEvent event = new EnrichedEvent(
                "it-evt-1", "orders-api-prod", "ERROR", 4, "checkout exploded",
                Instant.parse("2026-02-02T10:00:00Z"), Instant.now(), "pod-prod-3",
                "trace-xyz", "prod", Map.of("status", 500, "latency_ms", 1200), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonProvider provider = jsonpMapper.jsonProvider();
        try (JsonGenerator generator = provider.createGenerator(out)) {
            jsonpMapper.serialize(event, generator);
        }

        String json = out.toString();
        assertTrue(json.contains("\"id\":\"it-evt-1\""), json);
        assertTrue(json.contains("\"severity\":4"), json);
        assertTrue(json.contains("\"environment\":\"prod\""), json);
        // Instant must serialize as an ISO string, not fail or become an array.
        assertTrue(json.contains("2026-02-02T10:00:00Z"), json);
    }
}
