package dev.xj16.beacon.anomaly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xj16.beacon.common.EnrichedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin client for a local <a href="https://ollama.com">Ollama</a> instance.
 *
 * <p>Uses only the JDK HTTP client so there are no extra dependencies. The model is asked to rate
 * an event's anomalousness from 0 to 1; we parse the first float out of the reply. Every method is
 * defensive: connection errors, timeouts, and unparseable replies all resolve to
 * {@link Optional#empty()} so the caller can fall back cleanly. Nothing here requires a paid key.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final Pattern FLOAT = Pattern.compile("([01](?:\\.\\d+)?|0?\\.\\d+)");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    public OllamaClient(
            @Value("${beacon.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${beacon.ollama.model:llama3.2}") String model,
            @Value("${beacon.ollama.timeout-ms:2500}") long timeoutMs) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 1500)))
                .build();
    }

    /** Cheap reachability probe used to decide whether to attempt LLM scoring at all. */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ask the model to score an event. Returns empty on any failure so the caller falls back.
     */
    public Optional<Double> score(EnrichedEvent event) {
        String prompt = buildPrompt(event);
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of("temperature", 0));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode node = mapper.readTree(response.body());
            String text = node.path("response").asText("");
            return parseScore(text);
        } catch (Exception e) {
            log.debug("Ollama scoring failed, will fall back: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String buildPrompt(EnrichedEvent event) {
        return """
               You are an SRE assistant. Rate how anomalous this log event is on a scale from 0.0
               (perfectly normal) to 1.0 (a serious, unusual incident). Reply with ONLY the number.

               service: %s
               level: %s
               message: %s
               attributes: %s
               """.formatted(
                event.service(),
                event.level(),
                event.message(),
                event.attributes());
    }

    /** Extract the first [0,1] float from free-form model output. */
    static Optional<Double> parseScore(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = FLOAT.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1));
                if (v >= 0.0 && v <= 1.0) {
                    return Optional.of(v);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Optional.empty();
    }
}
