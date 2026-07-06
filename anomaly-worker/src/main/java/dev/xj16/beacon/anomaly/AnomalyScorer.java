package dev.xj16.beacon.anomaly;

import dev.xj16.beacon.common.EnrichedEvent;

/** Strategy that assigns a 0.0-1.0 anomaly score to an event. */
public interface AnomalyScorer {

    /**
     * @return anomaly score in {@code [0.0, 1.0]}; higher means more anomalous.
     */
    double score(EnrichedEvent event);

    /** Short identifier of the scorer used, surfaced in logs/metrics. */
    String name();
}
