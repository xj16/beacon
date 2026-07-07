package dev.xj16.beacon.anomaly.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A fired alert: the durable record that a scored event breached its service's {@link AlertRule}.
 * Persisted to the {@code alert} table and surfaced via {@code GET /api/anomaly/alerts}.
 */
@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "service")
    private String service;

    @Column(name = "team")
    private String team;

    @Column(name = "severity", nullable = false)
    private int severity;

    @Column(name = "anomaly_score", nullable = false)
    private double anomalyScore;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "message", length = 2048)
    private String message;

    @Column(name = "notified", nullable = false)
    private boolean notified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Alert() {
    }

    public Alert(String eventId, String service, String team, int severity, double anomalyScore,
                 String channel, String message) {
        this.eventId = eventId;
        this.service = service;
        this.team = team;
        this.severity = severity;
        this.anomalyScore = anomalyScore;
        this.channel = channel;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getService() {
        return service;
    }

    public String getTeam() {
        return team;
    }

    public int getSeverity() {
        return severity;
    }

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public String getChannel() {
        return channel;
    }

    public String getMessage() {
        return message;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
