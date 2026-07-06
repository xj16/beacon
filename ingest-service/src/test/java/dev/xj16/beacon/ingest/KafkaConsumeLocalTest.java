package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Local-only smoke test of the Kafka consume path using the in-JVM EmbeddedKafka broker and a
 * mocked Elasticsearch indexer. Proves the listener wiring + JSON serde + enrichment run without
 * requiring Docker, so the Kafka half of the pipeline can be validated on any machine.
 */
@SpringBootTest(properties = {
        "beacon.elasticsearch.uri=http://localhost:9200",
})
@EmbeddedKafka(
        partitions = 3,
        topics = "beacon.events",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class KafkaConsumeLocalTest {

    @MockBean
    ElasticsearchIndexer indexer;

    @Autowired
    KafkaTemplate<String, LogEvent> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Test
    void consumerReceivesAndIndexesEvent() throws Exception {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }

        LogEvent event = new LogEvent(
                "local-1", "orders-api-prod", "ERROR", "boom",
                Instant.parse("2026-02-02T10:00:00Z"), "pod-prod-3", "trace-xyz",
                Map.of("status", 500));

        kafkaTemplate.send("beacon.events", event.id(), event).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                verify(indexer, atLeastOnce()).index(any(EnrichedEvent.class)));
    }
}
