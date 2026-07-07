package dev.xj16.beacon.anomaly.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An alerting rule for a service, loaded from the {@code alert_rule} table that Postgres seeds via
 * {@code docker/postgres-init.sql} (and H2 seeds via {@code schema.sql}/{@code data.sql} for the
 * zero-infrastructure default). A scored event breaches this rule when its anomaly score is at
 * least {@link #minAnomalyScore} <em>and</em> its numeric severity is at least {@link #minSeverity}.
 */
@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Service this rule applies to; matched against {@code EnrichedEvent.service()}. */
    @Column(name = "service")
    private String service;

    @Column(name = "min_anomaly_score", nullable = false)
    private double minAnomalyScore = 0.8;

    @Column(name = "min_severity", nullable = false)
    private int minSeverity = 4;

    /** Notification channel key (e.g. {@code slack}); resolved to a {@code Notifier} at runtime. */
    @Column(name = "channel", nullable = false)
    private String channel = "slack";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AlertRule() {
    }

    public Long getId() {
        return id;
    }

    public String getService() {
        return service;
    }

    public double getMinAnomalyScore() {
        return minAnomalyScore;
    }

    public int getMinSeverity() {
        return minSeverity;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * True when a scored event breaches this rule: score at-or-above the threshold and severity
     * at-or-above the floor. A rule with no {@code service} is treated as a catch-all default.
     */
    public boolean matches(String eventService, double anomalyScore, int severity) {
        if (!enabled) {
            return false;
        }
        if (service != null && !service.isBlank() && !service.equals(eventService)) {
            return false;
        }
        return anomalyScore >= minAnomalyScore && severity >= minSeverity;
    }
}
