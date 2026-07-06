package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.LogEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Small REST surface for the ingest service:
 * <ul>
 *   <li>{@code POST /api/events} publishes an event onto Kafka (handy for demos / smoke tests),</li>
 *   <li>{@code GET /api/stats} reports how many events have been indexed so far.</li>
 * </ul>
 * The real ingestion path is the Kafka consumer; this controller is a convenience producer.
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
    public ResponseEntity<Map<String, Object>> publish(@RequestBody LogEvent event) {
        kafkaTemplate.send(topic, event.id(), event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "accepted", "id", event.id() == null ? "" : event.id()));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of("indexed", consumer.indexedCount());
    }
}
