package dev.xj16.beacon.ingest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test: publish an event to a real Kafka broker, let the consumer enrich
 * and index it into a real Elasticsearch, then query it back.
 *
 * <p>Requires a Docker daemon and so is disabled in the local "none" verify environment; it runs
 * in CI where Docker is available. The {@code *IT} suffix keeps it out of the default unit-test
 * task if the build is configured to split them.
 */
@SpringBootTest
@Testcontainers
class IngestPipelineIT {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static final ElasticsearchContainer ES =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.14.3"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    .waitingFor(Wait.forHttp("/").forPort(9200).forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("beacon.elasticsearch.uri", () -> "http://" + ES.getHttpHostAddress());
    }

    @Autowired
    KafkaTemplate<String, LogEvent> kafkaTemplate;

    @Autowired
    ElasticsearchClient esClient;

    @Autowired
    ElasticsearchIndexer indexer;

    @Test
    void eventFlowsFromKafkaIntoElasticsearch() throws Exception {
        indexer.ensureIndex();

        LogEvent event = new LogEvent(
                "it-evt-1", "orders-api-prod", "ERROR", "checkout exploded",
                Instant.parse("2026-02-02T10:00:00Z"), "pod-prod-3", "trace-xyz",
                Map.of("status", 500, "latency_ms", 1200));

        kafkaTemplate.send("beacon.events", event.id(), event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            esClient.indices().refresh(r -> r.index(indexer.index()));
            var response = esClient.get(g -> g.index(indexer.index()).id("it-evt-1"),
                    EnrichedEvent.class);
            assertTrue(response.found(), "document should be indexed");
            EnrichedEvent doc = response.source();
            assertEquals(4, doc.severity());
            assertEquals("prod", doc.environment());
        });
    }
}
