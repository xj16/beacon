package dev.xj16.beacon.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.xj16.beacon.common.LogEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Validated request body for {@code POST /api/events}. Keeping validation on a dedicated inbound DTO
 * (rather than the shared {@link LogEvent} domain record) means the wire contract can be strict at
 * the edge — bounded field sizes, a constrained {@code level}, a non-blank {@code message} — without
 * coupling those HTTP concerns to the Kafka payload type used throughout the pipeline.
 *
 * <p>This closes the "no input handling" gap: previously any JSON was published to Kafka unchecked.
 */
public record EventRequest(
        @Size(max = 256, message = "id must be at most 256 characters")
        @JsonProperty("id") String id,

        @NotBlank(message = "service is required")
        @Size(max = 256, message = "service must be at most 256 characters")
        @JsonProperty("service") String service,

        @Pattern(regexp = "(?i)debug|info|warn|error|fatal",
                message = "level must be one of DEBUG, INFO, WARN, ERROR, FATAL")
        @JsonProperty("level") String level,

        @NotBlank(message = "message is required")
        @Size(max = 8192, message = "message must be at most 8192 characters")
        @JsonProperty("message") String message,

        @JsonProperty("timestamp") Instant timestamp,

        @Size(max = 256, message = "host must be at most 256 characters")
        @JsonProperty("host") String host,

        @Size(max = 256, message = "trace_id must be at most 256 characters")
        @JsonProperty("trace_id") String traceId,

        @Size(max = 64, message = "at most 64 attributes are allowed")
        @JsonProperty("attributes") Map<String, Object> attributes
) {
    /** Convert this validated request into the pipeline's {@link LogEvent} domain type. */
    public LogEvent toLogEvent() {
        return new LogEvent(id, service, level, message, timestamp, host, traceId, attributes);
    }
}
