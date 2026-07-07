package dev.xj16.beacon.anomaly.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The catalogue of known services and their owning team, loaded from the {@code service_catalog}
 * table. Used to enrich fired alerts with a routing {@code team} so an operator knows who owns the
 * breaching service.
 */
@Entity
@Table(name = "service_catalog")
public class ServiceCatalog {

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "team")
    private String team;

    @Column(name = "environment", nullable = false)
    private String environment = "unknown";

    @Column(name = "created_at")
    private Instant createdAt;

    protected ServiceCatalog() {
    }

    public String getName() {
        return name;
    }

    public String getTeam() {
        return team;
    }

    public String getEnvironment() {
        return environment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
