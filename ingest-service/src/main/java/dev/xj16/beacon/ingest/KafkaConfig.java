package dev.xj16.beacon.ingest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.xj16.beacon.common.LogEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * Kafka wiring for the ingest service.
 *
 * <p>Declares the events topic and builds the producer/consumer factories with a Jackson
 * {@link ObjectMapper} that has the {@link JavaTimeModule} registered so {@code Instant} fields on
 * {@link LogEvent} serialize/deserialize correctly. The consumer's value deserializer is wrapped in
 * an {@link ErrorHandlingDeserializer} so a malformed record surfaces as a handled error instead of
 * stalling the listener.
 *
 * <p><strong>Reliability.</strong> The listener container is configured with:
 * <ul>
 *   <li>{@link ContainerProperties.AckMode#RECORD} — the consumer offset is committed only after
 *       the listener returns successfully, so a document is never marked consumed before it is
 *       indexed (at-least-once);</li>
 *   <li>a {@link DefaultErrorHandler} with {@linkplain ExponentialBackOff exponential backoff} that
 *       retries transient failures (e.g. a brief Elasticsearch blip) instead of dropping them;</li>
 *   <li>a {@link DeadLetterPublishingRecoverer} that routes a record which still fails after the
 *       retries — or which cannot be deserialized at all — to the
 *       {@code beacon.events.DLT} dead-letter topic, so a poison message never wedges the pipeline.
 * </ul>
 * This is what makes the README's "retry / route to a DLT" and "a spike never takes down producers"
 * claims real rather than aspirational.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public ProducerFactory<String, LogEvent> producerFactory() {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        JsonSerializer<LogEvent> valueSerializer = new JsonSerializer<>(objectMapper());
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, LogEvent> kafkaTemplate(
            ProducerFactory<String, LogEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Template used by the dead-letter recoverer. It must serialize whatever the failed record
     * actually carries, which depends on <em>how</em> the record failed:
     * <ul>
     *   <li>a <strong>deserialization</strong> failure surfaces the original raw {@code byte[]}
     *       payload (via {@link ErrorHandlingDeserializer}) — publish those bytes verbatim;</li>
     *   <li>an <strong>indexing</strong> failure (retries exhausted) carries a real
     *       {@link LogEvent} value — publish it as JSON.</li>
     * </ul>
     * The key is always a {@link String} (the consumer uses {@link StringDeserializer}), so a
     * {@link StringSerializer} keys the template and a {@link DelegatingByTypeSerializer} routes the
     * value by its runtime type. Getting this wrong is why a naive {@code byte[]/byte[]} template
     * throws {@code ClassCastException: String cannot be cast to [B} on publish.
     */
    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate() {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        // A single type-routing serializer used for both key and value. Keys are always Strings; the
        // value is a byte[] (deserialization failure) or a LogEvent (indexing failure).
        org.apache.kafka.common.serialization.Serializer<Object> serializer =
                new org.springframework.kafka.support.serializer.DelegatingByTypeSerializer(
                        Map.of(
                                byte[].class, new ByteArraySerializer(),
                                LogEvent.class, new JsonSerializer<>(objectMapper()),
                                String.class, new StringSerializer()));
        ProducerFactory<Object, Object> pf =
                new DefaultKafkaProducerFactory<>(props, serializer, serializer);
        return new KafkaTemplate<>(pf);
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

    /**
     * Routes failed/poison records to {@code <topic>.DLT} after retries are exhausted (or immediately
     * for records that cannot be deserialized). The template's {@link DelegatingByTypeSerializer}
     * copes with both a raw {@code byte[]} payload (deserialization failure) and a real
     * {@link LogEvent} (indexing failure).
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
    }

    /**
     * Error handler: retry with exponential backoff, then hand off to the dead-letter recoverer.
     * Deserialization failures are not retryable (they will never succeed) and go straight to the DLT.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(5_000L);
        backOff.setMaxElapsedTime(30_000L); // give up after ~30s of retrying, then DLT
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class);
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Retry {} for record at {}-{}@{}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        ex.getMessage()));
        return handler;
    }

    @Bean
    public org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, LogEvent>
            kafkaListenerContainerFactory(
                    ConsumerFactory<String, LogEvent> consumerFactory,
                    DefaultErrorHandler kafkaErrorHandler) {
        var factory =
                new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, LogEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // Commit the offset only after the listener successfully returns (at-least-once).
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
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

    /** The dead-letter topic that poison/failed records are routed to. */
    @Bean
    public NewTopic deadLetterTopic(
            @Value("${beacon.kafka.topic:beacon.events}") String topic,
            @Value("${beacon.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic + ".DLT")
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
