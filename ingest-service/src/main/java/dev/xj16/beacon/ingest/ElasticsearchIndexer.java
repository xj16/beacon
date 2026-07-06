package dev.xj16.beacon.ingest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import dev.xj16.beacon.common.EnrichedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes {@link EnrichedEvent}s into Elasticsearch, keyed by event id so re-delivery from Kafka is
 * idempotent. Also owns the index bootstrap: it creates the {@code beacon-events} index with an
 * explicit mapping the first time the service starts.
 */
@Component
public class ElasticsearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexer.class);

    private final ElasticsearchClient client;
    private final String index;

    public ElasticsearchIndexer(
            ElasticsearchClient client,
            @Value("${beacon.elasticsearch.index:beacon-events}") String index) {
        this.client = client;
        this.index = index;
    }

    public String index() {
        return index;
    }

    /**
     * Create the index with an explicit mapping if it does not already exist. Called on startup;
     * safe to call repeatedly.
     */
    public void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(e -> e.index(index)).value();
        if (exists) {
            log.info("Elasticsearch index '{}' already exists", index);
            return;
        }

        TypeMapping mapping = TypeMapping.of(m -> m
                .properties("id", Property.of(p -> p.keyword(k -> k)))
                .properties("service", Property.of(p -> p.keyword(k -> k)))
                .properties("level", Property.of(p -> p.keyword(k -> k)))
                .properties("severity", Property.of(p -> p.integer(i -> i)))
                // full-text on the message, with a keyword sub-field for exact matches/aggs
                .properties("message", Property.of(p -> p.text(t -> t
                        .fields("keyword", Property.of(f -> f.keyword(k -> k.ignoreAbove(1024)))))))
                .properties("timestamp", Property.of(p -> p.date(d -> d)))
                .properties("ingested_at", Property.of(p -> p.date(d -> d)))
                .properties("host", Property.of(p -> p.keyword(k -> k)))
                .properties("trace_id", Property.of(p -> p.keyword(k -> k)))
                .properties("environment", Property.of(p -> p.keyword(k -> k)))
                .properties("anomaly_score", Property.of(p -> p.float_(f -> f))));

        CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(index)
                .mappings(mapping));

        client.indices().create(request);
        log.info("Created Elasticsearch index '{}'", index);
    }

    /** Index a single enriched event, using its id as the document id (upsert semantics). */
    public void index(EnrichedEvent event) throws IOException {
        client.index(i -> i
                .index(index)
                .id(event.id())
                .document(event));
        log.debug("Indexed event id={} service={} severity={}",
                event.id(), event.service(), event.severity());
    }
}
