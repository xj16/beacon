package dev.xj16.beacon.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * An event after enrichment, ready to be indexed into Elasticsearch.
 *
 * <p>Enrichment adds a normalized severity number (useful for range queries and aggregations),
 * an ingest timestamp, a coarse environment tag derived from the service name, and an anomaly
 * score slot that the anomaly worker later fills in.
 *
 * @param id            document id (mirrors {@link LogEvent#id()})
 * @param service       emitting service
 * @param level         original severity string
 * @param severity      normalized numeric severity (DEBUG=1 .. FATAL=5)
 * @param message       log line
 * @param timestamp     event time
 * @param ingestedAt    time the ingest service processed the event
 * @param host          host / pod
 * @param traceId       trace id
 * @param environment   coarse environment bucket (prod / staging / dev / unknown)
 * @param attributes    structured attributes carried through from the raw event
 * @param anomalyScore  0.0-1.0 anomaly score; {@code null} until scored
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrichedEvent(
        @JsonProperty("id") String id,
        @JsonProperty("service") String service,
        @JsonProperty("level") String level,
        @JsonProperty("severity") int severity,
        @JsonProperty("message") String message,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("ingested_at") Instant ingestedAt,
        @JsonProperty("host") String host,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("environment") String environment,
        @JsonProperty("attributes") Map<String, Object> attributes,
        @JsonProperty("anomaly_score") Double anomalyScore
) {
    /** Return a copy of this event with the anomaly score set. */
    public EnrichedEvent withAnomalyScore(double score) {
        return new EnrichedEvent(
                id, service, level, severity, message, timestamp, ingestedAt,
                host, traceId, environment, attributes, score);
    }
}
