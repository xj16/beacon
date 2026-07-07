package dev.xj16.beacon.anomaly.alert;

import dev.xj16.beacon.common.EnrichedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The alerting engine that makes the Postgres box in the architecture diagram real.
 *
 * <p>After the {@code anomaly-worker} scores an event, it hands the scored {@link EnrichedEvent}
 * here. The service loads the enabled {@link AlertRule}s, finds the strictest rule the event
 * breaches (score {@code >=} {@code min_anomaly_score} <em>and</em> severity {@code >=}
 * {@code min_severity}), persists an {@link Alert}, and delivers it through the {@link Notifier}
 * that matches the rule's channel (falling back to the logging channel). It is idempotent per event
 * id so re-scoring never double-fires.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleRepository ruleRepository;
    private final ServiceCatalogRepository catalogRepository;
    private final AlertRepository alertRepository;
    private final Map<String, Notifier> notifiersByChannel;
    private final Notifier fallbackNotifier;
    private final Counter alertsFired;

    public AlertService(
            AlertRuleRepository ruleRepository,
            ServiceCatalogRepository catalogRepository,
            AlertRepository alertRepository,
            List<Notifier> notifiers,
            MeterRegistry meterRegistry) {
        this.ruleRepository = ruleRepository;
        this.catalogRepository = catalogRepository;
        this.alertRepository = alertRepository;
        this.notifiersByChannel = notifiers.stream()
                .collect(Collectors.toMap(Notifier::channel, Function.identity(), (a, b) -> a));
        this.fallbackNotifier = notifiers.stream()
                .filter(n -> "log".equals(n.channel()))
                .findFirst()
                .orElse(notifiers.isEmpty() ? null : notifiers.get(0));
        this.alertsFired = Counter.builder("beacon_alerts_fired_total")
                .description("Total alerts fired by the alerting engine")
                .register(meterRegistry);
        log.info("Alert engine ready with channels: {}", notifiersByChannel.keySet());
    }

    /**
     * Evaluate a freshly scored event. If it breaches a rule (and no alert already exists for it),
     * persist and deliver an alert. Returns the fired alert, or empty when nothing breached.
     */
    @Transactional
    public Optional<Alert> evaluate(EnrichedEvent event, double anomalyScore) {
        if (event == null || event.id() == null) {
            return Optional.empty();
        }

        Optional<AlertRule> breached = ruleRepository.findByEnabledTrue().stream()
                .filter(r -> r.matches(event.service(), anomalyScore, event.severity()))
                // Prefer the most specific / strictest rule when several match.
                .max((a, b) -> {
                    int svc = Boolean.compare(a.getService() != null, b.getService() != null);
                    if (svc != 0) {
                        return svc;
                    }
                    return Double.compare(a.getMinAnomalyScore(), b.getMinAnomalyScore());
                });

        if (breached.isEmpty()) {
            return Optional.empty();
        }

        if (alertRepository.existsByEventId(event.id())) {
            return Optional.empty();
        }

        AlertRule rule = breached.get();
        String team = catalogRepository.findById(
                        event.service() == null ? "" : event.service())
                .map(ServiceCatalog::getTeam)
                .orElse(null);

        Alert alert = new Alert(
                event.id(),
                event.service(),
                team,
                event.severity(),
                anomalyScore,
                rule.getChannel(),
                event.message());

        Notifier notifier = notifiersByChannel.getOrDefault(rule.getChannel(), fallbackNotifier);
        boolean delivered = false;
        if (notifier != null) {
            delivered = notifier.notify(alert);
            if (!delivered && notifier != fallbackNotifier && fallbackNotifier != null) {
                // Channel delivery failed (e.g. Slack down); make sure the alert is still surfaced.
                delivered = fallbackNotifier.notify(alert);
            }
        }
        alert.setNotified(delivered);

        Alert saved = alertRepository.save(alert);
        alertsFired.increment();
        return Optional.of(saved);
    }

    /** Recent alerts, newest first, for {@code GET /api/anomaly/alerts}. */
    @Transactional(readOnly = true)
    public List<Alert> recentAlerts(int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        return alertRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped));
    }
}
