package dev.xj16.beacon.anomaly;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.xj16.beacon.common.EnrichedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the scoring pass. Indexes unscored events into a real Elasticsearch, runs
 * one scoring batch (which falls back to the statistical scorer because no Ollama is present in
 * CI), and verifies scores are written back. Requires Docker; runs in CI.
 */
@SpringBootTest(properties = "beacon.ollama.base-url=http://localhost:1")
@Testcontainers
class AnomalyWorkerIT {

    @Container
    static final ElasticsearchContainer ES =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.14.3"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("beacon.elasticsearch.uri", () -> "http://" + ES.getHttpHostAddress());
    }

    @Autowired
    ElasticsearchClient client;

    @Autowired
    AnomalyWorker worker;

    @Autowired
    HybridScorer scorer;

    @Test
    void scoresUnscoredEvents() throws Exception {
        EnrichedEvent e1 = new EnrichedEvent(
                "a1", "orders-api-prod", "FATAL", 5, "OutOfMemory killed the pod",
                Instant.now(), Instant.now(), "host", null, "prod",
                Map.of("status", 500, "latency_ms", 9000), null);
        EnrichedEvent e2 = new EnrichedEvent(
                "a2", "orders-api-prod", "INFO", 2, "all good",
                Instant.now(), Instant.now(), "host", null, "prod",
                Map.of("status", 200), null);

        client.index(i -> i.index("beacon-events").id("a1").document(e1));
        client.index(i -> i.index("beacon-events").id("a2").document(e2));
        client.indices().refresh(r -> r.index("beacon-events"));

        int updated = worker.scoreOnce();
        assertEquals(2, updated);

        // In CI there is no Ollama, so the hybrid scorer must fall back to statistical.
        assertEquals("statistical", scorer.name());

        client.indices().refresh(r -> r.index("beacon-events"));
        var doc1 = client.get(g -> g.index("beacon-events").id("a1"), EnrichedEvent.class);
        assertNotNull(doc1.source().anomalyScore());
        assertTrue(doc1.source().anomalyScore() > 0.8, "fatal OOM should score high");

        var doc2 = client.get(g -> g.index("beacon-events").id("a2"), EnrichedEvent.class);
        assertNotNull(doc2.source().anomalyScore());
        assertTrue(doc2.source().anomalyScore() < 0.3, "benign info should score low");

        // Re-running should find nothing new to score.
        client.indices().refresh(r -> r.index("beacon-events"));
        assertEquals(0, worker.scoreOnce());
    }
}
