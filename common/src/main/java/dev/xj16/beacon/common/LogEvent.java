package dev.xj16.beacon.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Raw observability event as it arrives on the Kafka topic.
 *
 * <p>Producers (application services, agents, sidecars) publish JSON payloads shaped like this
 * onto the {@code beacon.events} topic. The ingest service deserializes them, enriches them, and
 * indexes an {@link EnrichedEvent} into Elasticsearch.
 *
 * @param id        globally unique event id (producer-supplied, used as the ES document id so
 *                  re-delivery is idempotent)
 * @param service   logical name of the emitting service, e.g. {@code checkout-api}
 * @param level     severity: one of DEBUG, INFO, WARN, ERROR, FATAL
 * @param message   human-readable log line
 * @param timestamp event time (producer clock)
 * @param host      host / pod the event came from
 * @param traceId   distributed-trace id, when available
 * @param attributes free-form structured attributes (latency, status code, user id, ...)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEvent(
        @JsonProperty("id") String id,
        @JsonProperty("service") String service,
        @JsonProperty("level") String level,
        @JsonProperty("message") String message,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("host") String host,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("attributes") Map<String, Object> attributes
) {
    public LogEvent {
        if (attributes == null) {
            attributes = Map.of();
        }
    }

    /** True when this event carries an error-class severity. */
    public boolean isError() {
        if (level == null) {
            return false;
        }
        String upper = level.toUpperCase();
        return upper.equals("ERROR") || upper.equals("FATAL");
    }
}
