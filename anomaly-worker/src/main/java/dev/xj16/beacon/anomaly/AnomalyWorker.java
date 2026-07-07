package dev.xj16.beacon.anomaly;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import dev.xj16.beacon.anomaly.alert.AlertService;
import dev.xj16.beacon.common.EnrichedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scheduled worker that finds events which have not yet been scored, scores them with the
 * {@link HybridScorer}, writes the {@code anomaly_score} back onto each document, and hands each
 * scored event to the {@link AlertService} so breaching events fire alerts.
 *
 * <p>Score write-back uses a single Elasticsearch {@code _bulk} request per batch rather than one
 * HTTP round-trip per event, so the pipeline sustains a firehose instead of stalling on network
 * latency. Running on a fixed delay keeps Elasticsearch load predictable.
 */
@Component
public class AnomalyWorker {

    private static final Logger log = LoggerFactory.getLogger(AnomalyWorker.class);

    private final ElasticsearchClient client;
    private final HybridScorer scorer;
    private final AlertService alertService;
    private final String index;
    private final int batchSize;
    private final Timer scoringTimer;

    public AnomalyWorker(
            ElasticsearchClient client,
            HybridScorer scorer,
            AlertService alertService,
            MeterRegistry meterRegistry,
            @Value("${beacon.elasticsearch.index:beacon-events}") String index,
            @Value("${beacon.anomaly.batch-size:100}") int batchSize) {
        this.client = client;
        this.scorer = scorer;
        this.alertService = alertService;
        this.index = index;
        this.batchSize = batchSize;
        this.scoringTimer = Timer.builder("beacon_scoring_latency")
                .description("Time to score one batch of events")
                .register(meterRegistry);
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

        // Score the whole batch, then flush the score write-backs in a single _bulk request.
        record Scored(String id, EnrichedEvent event, double score) {
        }
        List<Scored> scored = new ArrayList<>(hits.size());
        long start = System.nanoTime();
        for (var hit : hits) {
            EnrichedEvent event = hit.source();
            if (event == null) {
                continue;
            }
            scored.add(new Scored(hit.id(), event, scorer.score(event)));
        }
        scoringTimer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);

        if (scored.isEmpty()) {
            return 0;
        }

        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (Scored s : scored) {
            bulk.operations(op -> op.update(u -> u
                    .index(index)
                    .id(s.id())
                    .action(a -> a.doc(Map.of("anomaly_score", s.score())))));
        }
        BulkResponse bulkResponse = client.bulk(bulk.build());
        if (bulkResponse.errors()) {
            long failures = bulkResponse.items().stream()
                    .filter(i -> i.error() != null)
                    .count();
            log.warn("Bulk score write-back had {} failed item(s)", failures);
        }

        // Evaluate alert rules against each scored event (idempotent per event id).
        int alertsFired = 0;
        for (Scored s : scored) {
            if (alertService.evaluate(s.event(), s.score()).isPresent()) {
                alertsFired++;
            }
        }

        int updated = scored.size();
        log.info("Scored {} events using {} backend ({} alert(s) fired)",
                updated, scorer.name(), alertsFired);
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
