package dev.xj16.beacon.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures the Elasticsearch index exists once the application is ready. Failures are logged but do
 * not crash startup, so the service can boot even if Elasticsearch is briefly unavailable; the
 * index is retried lazily on the first index() call path in real deployments.
 */
@Component
public class IndexBootstrap {

    private static final Logger log = LoggerFactory.getLogger(IndexBootstrap.class);

    private final ElasticsearchIndexer indexer;

    public IndexBootstrap(ElasticsearchIndexer indexer) {
        this.indexer = indexer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            indexer.ensureIndex();
        } catch (Exception e) {
            log.warn("Could not ensure Elasticsearch index on startup: {}", e.getMessage());
        }
    }
}
