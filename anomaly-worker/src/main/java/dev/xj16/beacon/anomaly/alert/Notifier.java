package dev.xj16.beacon.anomaly.alert;

/**
 * A destination that a fired {@link Alert} is delivered to. Implementations are pluggable: the
 * default {@link LoggingNotifier} is always available, and {@link SlackNotifier} activates only when
 * a webhook URL is configured. This keeps Beacon fully functional with zero external services while
 * demonstrating a real integration when one is wired up.
 */
public interface Notifier {

    /**
     * Deliver the alert. Must not throw — implementations swallow/transform delivery failures and
     * return {@code false} so a flaky notification never breaks the scoring pipeline.
     *
     * @return true if the alert was delivered
     */
    boolean notify(Alert alert);

    /** Short channel key this notifier serves, e.g. {@code slack} or {@code log}. */
    String channel();
}
