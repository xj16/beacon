package dev.xj16.beacon.ingest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test for the ingest pipeline: publish an event to Kafka, let the
 * {@code @KafkaListener} enrich and index it into a real Elasticsearch, then read it back.
 *
 * <p>Kafka is provided by Spring's in-JVM {@link EmbeddedKafka} broker (deterministic and fast,
 * exercising the exact same consumer + JSON serde + enrichment + indexing code path), while
 * Elasticsearch is a real Testcontainers instance started in a static initializer so its mapped
 * port is available when Spring resolves {@code @DynamicPropertySource}. Requires Docker for the
 * Elasticsearch container; runs in CI via the {@code integrationTest} task.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = "beacon.events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class IngestPipelineIT {

    static final ElasticsearchContainer ES =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.14.3"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    .withStartupTimeout(Duration.ofMinutes(3));

    static {
        ES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // Kafka's broker address is injected by @EmbeddedKafka via bootstrapServersProperty above.
        registry.add("beacon.elasticsearch.uri", () -> "http://" + ES.getHttpHostAddress());
    }

    @Autowired
    KafkaTemplate<String, LogEvent> kafkaTemplate;

    @Autowired
    ElasticsearchClient esClient;

    @Autowired
    ElasticsearchIndexer indexer;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    EventConsumer consumer;

    @Test
    void eventFlowsFromKafkaIntoElasticsearch() throws Exception {
        indexer.ensureIndex();

        // Ensure the listener has all partitions assigned before we publish, so the event is not
        // missed. The topic is provisioned with 3 partitions by the NewTopic bean.
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }

        long before = consumer.indexedCount();

        LogEvent event = new LogEvent(
                "it-evt-1", "orders-api-prod", "ERROR", "checkout exploded",
                Instant.parse("2026-02-02T10:00:00Z"), "pod-prod-3", "trace-xyz",
                Map.of("status", 500, "latency_ms", 1200));

        kafkaTemplate.send("beacon.events", event.id(), event).get();

        // Stage 1: the Kafka listener consumed the event and successfully indexed it (no throw).
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() ->
                        assertTrue(consumer.indexedCount() > before,
                                "listener should have consumed and indexed the event"));

        // Stage 2: the document is retrievable from Elasticsearch with the enriched fields.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
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
