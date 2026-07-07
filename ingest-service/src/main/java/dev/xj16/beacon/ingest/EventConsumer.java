package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer that drives the ingest pipeline: raw event in -> enrich -> index into
 * Elasticsearch. Exposes a counter of successfully indexed events for the health/metrics surface
 * and for integration tests to assert against.
 *
 * <p>On an indexing failure it rethrows an {@link IngestException}; the container's
 * {@code DefaultErrorHandler} (configured in {@link KafkaConfig}) then retries with backoff and, if
 * the failure persists, routes the record to the {@code beacon.events.DLT} dead-letter topic. The
 * offset is committed only after this method returns successfully (RECORD ack), so a document is
 * never marked consumed before it is durably indexed.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final EnrichmentService enrichmentService;
    private final ElasticsearchIndexer indexer;
    private final AtomicLong indexedCount = new AtomicLong();
    private final Counter indexedCounter;
    private final Timer indexTimer;

    public EventConsumer(EnrichmentService enrichmentService, ElasticsearchIndexer indexer,
                         MeterRegistry meterRegistry) {
        this.enrichmentService = enrichmentService;
        this.indexer = indexer;
        this.indexedCounter = Counter.builder("beacon_events_indexed_total")
                .description("Total events enriched and indexed into Elasticsearch")
                .register(meterRegistry);
        this.indexTimer = Timer.builder("beacon_index_latency")
                .description("Time to enrich and index a single event")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${beacon.kafka.topic:beacon.events}",
            groupId = "${beacon.kafka.group-id:beacon-ingest}")
    public void onEvent(LogEvent event) {
        long start = System.nanoTime();
        try {
            EnrichedEvent enriched = enrichmentService.enrich(event);
            indexer.index(enriched);
            indexedCount.incrementAndGet();
            indexedCounter.increment();
            indexTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (IOException e) {
            // Rethrow so the container's DefaultErrorHandler retries with backoff and, on exhaustion,
            // routes this record to the beacon.events.DLT dead-letter topic.
            log.error("Failed to index event id={}", event.id(), e);
            throw new IngestException("indexing failed for event " + event.id(), e);
        }
    }

    public long indexedCount() {
        return indexedCount.get();
    }
}
