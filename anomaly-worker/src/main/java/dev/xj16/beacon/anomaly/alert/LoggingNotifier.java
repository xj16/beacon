package dev.xj16.beacon.anomaly.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The always-available fallback notifier. It logs a structured warning so alerts are visible with
 * no external configuration, and it is the channel used whenever no better-matched notifier exists.
 */
@Component
public class LoggingNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger("beacon.alert");

    @Override
    public boolean notify(Alert alert) {
        log.warn("ALERT [{}] service={} team={} severity={} score={} :: {}",
                alert.getChannel(),
                alert.getService(),
                alert.getTeam(),
                alert.getSeverity(),
                String.format("%.2f", alert.getAnomalyScore()),
                alert.getMessage());
        return true;
    }

    @Override
    public String channel() {
        return "log";
    }
}
