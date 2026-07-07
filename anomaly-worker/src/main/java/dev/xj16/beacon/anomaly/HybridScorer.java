package dev.xj16.beacon.anomaly;

import dev.xj16.beacon.common.EnrichedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The scorer the worker actually uses.
 *
 * <p>Prefers the local LLM ({@link OllamaClient}) when it is reachable, and transparently falls
 * back to the {@link StatisticalScorer} when Ollama is absent, times out, or returns an unusable
 * answer. This keeps the project 100% free and runnable with no external services while still
 * demonstrating a real local-LLM integration when one is present.
 */
@Component
@Primary
public class HybridScorer implements AnomalyScorer {

    private static final Logger log = LoggerFactory.getLogger(HybridScorer.class);

    private final OllamaClient ollama;
    private final StatisticalScorer fallback;
    private volatile boolean llmAvailable;

    public HybridScorer(OllamaClient ollama, StatisticalScorer fallback, MeterRegistry meterRegistry) {
        this.ollama = ollama;
        this.fallback = fallback;
        this.llmAvailable = ollama.isAvailable();
        log.info("Anomaly scoring backend: {}", llmAvailable ? "ollama (local LLM)" : "statistical (fallback)");
        // Gauge: 1.0 when the local LLM backend is active, 0.0 when on the statistical fallback.
        meterRegistry.gauge("beacon_anomaly_backend_llm", this,
                s -> s.llmAvailable ? 1.0 : 0.0);
    }

    /** Re-probe LLM availability; called periodically by the worker so it can recover. */
    public void refreshAvailability() {
        this.llmAvailable = ollama.isAvailable();
    }

    @Override
    public double score(EnrichedEvent event) {
        if (llmAvailable) {
            Optional<Double> llmScore = ollama.score(event);
            if (llmScore.isPresent()) {
                return llmScore.get();
            }
            // one miss shouldn't disable the LLM permanently, but a hard failure should
            // route this event to the fallback immediately.
        }
        return fallback.score(event);
    }

    @Override
    public String name() {
        return llmAvailable ? "ollama" : "statistical";
    }

    public boolean isLlmAvailable() {
        return llmAvailable;
    }
}
