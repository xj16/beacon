package dev.xj16.beacon.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Beacon ingest service.
 *
 * <p>Consumes raw {@code LogEvent}s from Kafka, enriches them, and indexes the result into
 * Elasticsearch. Exposes actuator health/metrics and a small REST endpoint for manual ingestion.
 */
@SpringBootApplication
public class IngestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestServiceApplication.class, args);
    }
}
