package dev.xj16.beacon.anomaly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Beacon anomaly worker.
 *
 * <p>Periodically pulls recent events from Elasticsearch, scores each one for anomalousness, and
 * writes the score back onto the document. Scoring prefers a local LLM (Ollama) when reachable and
 * falls back to a fast, deterministic statistical scorer otherwise, so the worker is fully
 * functional with zero external dependencies or paid keys.
 */
@SpringBootApplication
@EnableScheduling
public class AnomalyWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnomalyWorkerApplication.class, args);
    }
}
