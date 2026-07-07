package dev.xj16.beacon.anomaly.alert;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads the service catalogue for alert routing (team lookup). */
public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, String> {
}
