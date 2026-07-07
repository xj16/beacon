package dev.xj16.beacon.anomaly.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Reads alerting rules seeded into the metadata store. */
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabledTrue();
}
