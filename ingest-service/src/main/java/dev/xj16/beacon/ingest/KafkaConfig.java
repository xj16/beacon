package dev.xj16.beacon.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.xj16.beacon.common.LogEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Kafka wiring for the ingest service.
 *
 * <p>Declares the events topic and, crucially, builds the producer/consumer factories with a
 * Jackson {@link ObjectMapper} that has the {@link JavaTimeModule} registered so {@code Instant}
 * fields on {@link LogEvent} serialize/deserialize correctly. The consumer's value deserializer is
 * wrapped in an {@link ErrorHandlingDeserializer} so a malformed record surfaces as a handled error
 * instead of stalling the listener.
 */
@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public ProducerFactory<String, LogEvent> producerFactory() {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        JsonSerializer<LogEvent> valueSerializer = new JsonSerializer<>(objectMapper());
        DefaultKafkaProducerFactory<String, LogEvent> factory =
                new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
        return factory;
    }

    @Bean
    public org.springframework.kafka.core.KafkaTemplate<String, LogEvent> kafkaTemplate(
            ProducerFactory<String, LogEvent> producerFactory) {
        return new org.springframework.kafka.core.KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, LogEvent> consumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        JsonDeserializer<LogEvent> jsonDeserializer =
                new JsonDeserializer<>(LogEvent.class, objectMapper());
        jsonDeserializer.addTrustedPackages("dev.xj16.beacon.common");
        jsonDeserializer.setUseTypeHeaders(false); // always deserialize as LogEvent

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, LogEvent>
            kafkaListenerContainerFactory(ConsumerFactory<String, LogEvent> consumerFactory) {
        var factory =
                new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, LogEvent>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

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
