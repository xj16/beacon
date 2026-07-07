package dev.xj16.beacon.anomaly.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires optional notifiers. The {@link SlackNotifier} is registered only when a non-blank webhook
 * URL is configured, so the default zero-config profile relies purely on the always-present
 * {@link LoggingNotifier} and never makes a network call.
 */
@Configuration
public class AlertConfig {

    @Bean
    public SlackNotifier slackNotifier(
            @Value("${beacon.alert.slack-webhook-url:}") String webhookUrl) {
        if (!StringUtils.hasText(webhookUrl)) {
            return null; // no bean registered -> only the logging channel is active
        }
        return new SlackNotifier(webhookUrl);
    }
}
