package dev.xj16.beacon.anomaly.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Posts alerts to a Slack (or Slack-compatible) incoming webhook. Registered only when
 * {@code beacon.alert.slack-webhook-url} / {@code SLACK_WEBHOOK_URL} is set, so the default
 * zero-config profile never attempts a network call. Uses only the JDK HTTP client (no extra
 * dependency) and never throws: a delivery failure is logged and reported as {@code false} so the
 * caller can fall back to the logging channel.
 *
 * <p>Instantiated only when a webhook URL is configured — see {@code AlertConfig} — so the default
 * zero-config profile has no Slack notifier registered at all.
 */
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final String webhookUrl;

    public SlackNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        log.info("Slack alert notifier enabled");
    }

    @Override
    public boolean notify(Alert alert) {
        try {
            String text = String.format(
                    ":rotating_light: *Beacon anomaly* on `%s` (team %s)\nseverity %d · score %.2f · event `%s`\n> %s",
                    alert.getService(),
                    alert.getTeam() == null ? "unknown" : alert.getTeam(),
                    alert.getSeverity(),
                    alert.getAnomalyScore(),
                    alert.getEventId(),
                    alert.getMessage() == null ? "" : alert.getMessage());

            String payload = mapper.writeValueAsString(Map.of("text", text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                log.warn("Slack webhook returned status {}", response.statusCode());
            }
            return ok;
        } catch (Exception e) {
            log.warn("Slack alert delivery failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String channel() {
        return "slack";
    }
}
