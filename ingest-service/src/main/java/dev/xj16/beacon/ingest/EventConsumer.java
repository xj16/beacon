package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer that drives the ingest pipeline: raw event in -> enrich -> index into
 * Elasticsearch. Exposes a counter of successfully indexed events for the health/metrics surface
 * and for integration tests to assert against.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final EnrichmentService enrichmentService;
    private final ElasticsearchIndexer indexer;
    private final AtomicLong indexedCount = new AtomicLong();

    public EventConsumer(EnrichmentService enrichmentService, ElasticsearchIndexer indexer) {
        this.enrichmentService = enrichmentService;
        this.indexer = indexer;
    }

    @KafkaListener(
            topics = "${beacon.kafka.topic:beacon.events}",
            groupId = "${beacon.kafka.group-id:beacon-ingest}")
    public void onEvent(LogEvent event) {
        try {
            EnrichedEvent enriched = enrichmentService.enrich(event);
            indexer.index(enriched);
            indexedCount.incrementAndGet();
        } catch (IOException e) {
            // Rethrow so Spring Kafka's error handler can retry / route to a DLT.
            log.error("Failed to index event id={}", event.id(), e);
            throw new IngestException("indexing failed for event " + event.id(), e);
        }
    }

    public long indexedCount() {
        return indexedCount.get();
    }
}
