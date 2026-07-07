package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.LogEvent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Small REST surface for the ingest service:
 * <ul>
 *   <li>{@code POST /api/events} publishes an event onto Kafka (handy for demos / smoke tests),</li>
 *   <li>{@code GET /api/stats} reports how many events have been indexed so far.</li>
 * </ul>
 * The real ingestion path is the Kafka consumer; this controller is a convenience producer. The
 * request body is validated ({@link EventRequest}) so malformed input is rejected with a clean 400
 * at the edge instead of being published unchecked.
 */
@RestController
@RequestMapping("/api")
public class IngestController {

    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final EventConsumer consumer;
    private final String topic;

    public IngestController(
            KafkaTemplate<String, LogEvent> kafkaTemplate,
            EventConsumer consumer,
            org.springframework.core.env.Environment env) {
        this.kafkaTemplate = kafkaTemplate;
        this.consumer = consumer;
        this.topic = env.getProperty("beacon.kafka.topic", "beacon.events");
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> publish(@Valid @RequestBody EventRequest request) {
        // Backfill an id if the producer omitted one, so the Kafka key and response are consistent.
        String id = (request.id() == null || request.id().isBlank())
                ? UUID.randomUUID().toString()
                : request.id();
        LogEvent event = new LogEvent(
                id, request.service(), request.level(), request.message(),
                request.timestamp(), request.host(), request.traceId(), request.attributes());
        kafkaTemplate.send(topic, id, event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "accepted", "id", id));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("indexed", consumer.indexedCount());
    }
}
