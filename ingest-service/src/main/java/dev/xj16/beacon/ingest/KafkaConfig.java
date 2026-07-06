package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.LogEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic provisioning. Spring Boot autoconfigures the consumer/producer factories from
 * {@code spring.kafka.*} properties (including the JSON deserializer for {@link LogEvent}); this
 * class only declares the topic so a fresh broker has it created automatically.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic eventsTopic(
            @Value("${beacon.kafka.topic:beacon.events}") String topic,
            @Value("${beacon.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
