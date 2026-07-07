package dev.xj16.beacon.anomaly;

import dev.xj16.beacon.anomaly.alert.Alert;
import dev.xj16.beacon.anomaly.alert.AlertService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Operator endpoints for the anomaly worker:
 * <ul>
 *   <li>{@code GET  /api/anomaly/status} reports the active scoring backend,</li>
 *   <li>{@code POST /api/anomaly/scan}   triggers an immediate scoring pass,</li>
 *   <li>{@code GET  /api/anomaly/alerts} lists recently fired alerts.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/anomaly")
public class AnomalyController {

    private final AnomalyWorker worker;
    private final HybridScorer scorer;
    private final AlertService alertService;

    public AnomalyController(AnomalyWorker worker, HybridScorer scorer, AlertService alertService) {
        this.worker = worker;
        this.scorer = scorer;
        this.alertService = alertService;
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

    @GetMapping("/alerts")
    public List<Alert> alerts(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return alertService.recentAlerts(limit);
    }
}
