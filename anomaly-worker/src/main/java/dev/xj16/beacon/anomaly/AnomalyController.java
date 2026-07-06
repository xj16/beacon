package dev.xj16.beacon.anomaly;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Operator endpoints for the anomaly worker:
 * <ul>
 *   <li>{@code GET /api/anomaly/status} reports the active scoring backend,</li>
 *   <li>{@code POST /api/anomaly/scan} triggers an immediate scoring pass.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/anomaly")
public class AnomalyController {

    private final AnomalyWorker worker;
    private final HybridScorer scorer;

    public AnomalyController(AnomalyWorker worker, HybridScorer scorer) {
        this.worker = worker;
        this.scorer = scorer;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "backend", scorer.name(),
                "llmAvailable", scorer.isLlmAvailable());
    }

    @PostMapping("/scan")
    public Map<String, Object> scan() throws IOException {
        int updated = worker.scoreOnce();
        return Map.of("updated", updated, "backend", scorer.name());
    }
}
