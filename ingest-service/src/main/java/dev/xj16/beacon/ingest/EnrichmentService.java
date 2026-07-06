package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.EnrichedEvent;
import dev.xj16.beacon.common.LogEvent;
import dev.xj16.beacon.common.Severity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a raw {@link LogEvent} into an {@link EnrichedEvent}.
 *
 * <p>Enrichment is deliberately pure and side-effect free so it is trivially unit-testable:
 * <ul>
 *   <li>normalizes the textual level into a numeric {@code severity},</li>
 *   <li>stamps an {@code ingestedAt} time,</li>
 *   <li>derives a coarse {@code environment} bucket from naming conventions,</li>
 *   <li>backfills a synthetic id/timestamp when a producer omits them.</li>
 * </ul>
 */
@Service
public class EnrichmentService {

    /** Derive environment from service-name / host suffixes commonly used in deployments. */
    static String deriveEnvironment(LogEvent event) {
        String haystack = ((event.service() == null ? "" : event.service()) + " "
                + (event.host() == null ? "" : event.host()))
                .toLowerCase(Locale.ROOT);
        if (haystack.contains("prod")) {
            return "prod";
        }
        if (haystack.contains("staging") || haystack.contains("stage")) {
            return "staging";
        }
        if (haystack.contains("dev") || haystack.contains("local")) {
            return "dev";
        }
        return "unknown";
    }

    public EnrichedEvent enrich(LogEvent event) {
        String id = (event.id() == null || event.id().isBlank())
                ? java.util.UUID.randomUUID().toString()
                : event.id();

        Instant timestamp = event.timestamp() != null ? event.timestamp() : Instant.now();

        Map<String, Object> attributes = new HashMap<>(event.attributes());

        return new EnrichedEvent(
                id,
                event.service() == null ? "unknown" : event.service(),
                event.level(),
                Severity.toNumeric(event.level()),
                event.message(),
                timestamp,
                Instant.now(),
                event.host(),
                event.traceId(),
                deriveEnvironment(event),
                attributes,
                null
        );
    }
}
