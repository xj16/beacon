package dev.xj16.beacon.ingest;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reliability integration test: proves the dead-letter path actually exists. A record that cannot be
 * deserialized into a {@link dev.xj16.beacon.common.LogEvent} must not silently vanish or wedge the
 * listener — the container's {@code DefaultErrorHandler} + {@code DeadLetterPublishingRecoverer}
 * (configured in {@link KafkaConfig}) route it to the {@code beacon.events.DLT} topic. This test
 * publishes a poison (malformed-JSON) record and asserts it lands on the DLT.
 *
 * <p>Uses Spring's in-JVM {@link EmbeddedKafka} broker. Elasticsearch is a real Testcontainers
 * instance only so the full application context starts; it is never exercised here.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"beacon.events", "beacon.events.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class DeadLetterIT {

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
        registry.add("beacon.elasticsearch.uri", () -> "http://" + ES.getHttpHostAddress());
    }

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void poisonRecordIsRoutedToDeadLetterTopic() {
        // A raw string producer lets us publish bytes that will fail JSON deserialization on consume.
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(broker));
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(
                producerProps, new StringSerializer(), new StringSerializer());
        KafkaTemplate<String, String> rawTemplate = new KafkaTemplate<>(pf);

        rawTemplate.send("beacon.events", "poison-1", "this is not valid json for a LogEvent {{{");
        rawTemplate.flush();

        // Consume from the dead-letter topic and assert the poison record arrived.
        Map<String, Object> consumerProps =
                new HashMap<>(KafkaTestUtils.consumerProps("dlt-verify-group", "true", broker));
        consumerProps.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(java.util.List.of("beacon.events.DLT"));
            ConsumerRecord<String, String> dltRecord =
                    KafkaTestUtils.getSingleRecord(consumer, "beacon.events.DLT", Duration.ofSeconds(30));
            assertNotNull(dltRecord, "poison record should have been routed to the DLT");
            assertEquals("poison-1", dltRecord.key());
        }
    }
}
