package dev.xj16.beacon.anomaly;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import dev.xj16.beacon.common.EnrichedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Scheduled worker that finds events which have not yet been scored, scores them with the
 * {@link HybridScorer}, and writes the {@code anomaly_score} back onto each document.
 *
 * <p>It queries for documents missing the {@code anomaly_score} field, scores a bounded batch, and
 * updates them. Running on a fixed delay keeps Elasticsearch load predictable.
 */
@Component
public class AnomalyWorker {

    private static final Logger log = LoggerFactory.getLogger(AnomalyWorker.class);

    private final ElasticsearchClient client;
    private final HybridScorer scorer;
    private final String index;
    private final int batchSize;

    public AnomalyWorker(
            ElasticsearchClient client,
            HybridScorer scorer,
            @Value("${beacon.elasticsearch.index:beacon-events}") String index,
            @Value("${beacon.anomaly.batch-size:100}") int batchSize) {
        this.client = client;
        this.scorer = scorer;
        this.index = index;
        this.batchSize = batchSize;
    }

    /**
     * Score one batch of unscored events. Returns the number of events updated so callers/tests can
     * assert progress.
     */
    public int scoreOnce() throws IOException {
        // Match documents that do NOT have an anomaly_score yet.
        Query missingScore = Query.of(q -> q
                .bool(b -> b.mustNot(mn -> mn.exists(e -> e.field("anomaly_score")))));

        var response = client.search(s -> s
                .index(index)
                .size(batchSize)
                .query(missingScore), EnrichedEvent.class);

        List<? extends co.elastic.clients.elasticsearch.core.search.Hit<EnrichedEvent>> hits =
                response.hits().hits();
        if (hits.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (var hit : hits) {
            EnrichedEvent event = hit.source();
            if (event == null) {
                continue;
            }
            double score = scorer.score(event);
            String id = hit.id();
            client.update(u -> u
                    .index(index)
                    .id(id)
                    .doc(Map.of("anomaly_score", score)), EnrichedEvent.class);
            updated++;
        }
        log.info("Scored {} events using {} backend", updated, scorer.name());
        return updated;
    }

    @Scheduled(fixedDelayString = "${beacon.anomaly.interval-ms:15000}")
    public void scheduledScan() {
        try {
            scorer.refreshAvailability();
            int updated = scoreOnce();
            if (updated == 0) {
                log.debug("No unscored events found");
            }
        } catch (Exception e) {
            log.warn("Anomaly scan failed: {}", e.getMessage());
        }
    }
}
