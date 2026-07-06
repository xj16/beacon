package dev.xj16.beacon.anomaly;

import dev.xj16.beacon.common.EnrichedEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, dependency-free anomaly scorer.
 *
 * <p>Combines a handful of cheap heuristics that correlate with genuinely interesting events:
 * <ul>
 *   <li>severity weight (FATAL/ERROR dominate the score),</li>
 *   <li>presence of high-signal keywords ("outofmemory", "timeout", "panic", ...),</li>
 *   <li>elevated latency in the {@code latency_ms} attribute,</li>
 *   <li>5xx HTTP status codes in the {@code status} attribute.</li>
 * </ul>
 *
 * <p>The result is clamped to {@code [0,1]}. This is the fallback used whenever the local LLM is
 * unavailable, and it is good enough to be useful on its own.
 */
@Component
public class StatisticalScorer implements AnomalyScorer {

    private static final Set<String> HIGH_SIGNAL = Set.of(
            "outofmemory", "oom", "timeout", "panic", "deadlock", "segfault",
            "corrupt", "exception", "fatal", "refused", "unreachable", "throttle");

    @Override
    public double score(EnrichedEvent event) {
        double score = 0.0;

        // Severity contributes up to 0.5 (severity 1..5 -> 0.0..0.5).
        score += Math.max(0, event.severity() - 1) / 8.0;

        // Keyword hits in the message.
        String message = event.message() == null ? "" : event.message().toLowerCase(Locale.ROOT);
        for (String kw : HIGH_SIGNAL) {
            if (message.contains(kw)) {
                score += 0.2;
                break; // one keyword bonus is enough; don't stack unboundedly
            }
        }

        // Latency attribute.
        Object latency = event.attributes().get("latency_ms");
        if (latency instanceof Number n) {
            double ms = n.doubleValue();
            if (ms > 5000) {
                score += 0.3;
            } else if (ms > 1000) {
                score += 0.15;
            }
        }

        // 5xx status codes.
        Object status = event.attributes().get("status");
        if (status instanceof Number n) {
            int code = n.intValue();
            if (code >= 500) {
                score += 0.25;
            }
        }

        return clamp(score);
    }

    static double clamp(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    @Override
    public String name() {
        return "statistical";
    }
}
