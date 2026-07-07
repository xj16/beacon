package dev.xj16.beacon.anomaly.alert;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persists and lists fired alerts. */
public interface AlertRepository extends JpaRepository<Alert, Long> {

    /** Most-recent alerts first, capped by the caller's {@link Pageable}. */
    List<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Guard against duplicate alerts when an event is (re-)scored more than once. */
    boolean existsByEventId(String eventId);
}
