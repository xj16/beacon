package dev.xj16.beacon.ingest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small, dependency-free fixed-window rate limiter on the write edge ({@code POST /api/events}).
 * Each client IP gets at most {@code beacon.ratelimit.per-minute} requests per rolling one-minute
 * window; excess requests get {@code 429 Too Many Requests} with a {@code Retry-After} header. This
 * removes the "no rate limiting, a client can flood the ingest edge" gap without pulling in a
 * heavyweight dependency. Disabled by setting the limit to {@code 0}.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int perMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${beacon.ratelimit.per-minute:600}") int perMinute) {
        this.perMinute = perMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only guard the mutating API surface; leave actuator/health unthrottled.
        return perMinute <= 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = clientKey(request);
        long minute = System.currentTimeMillis() / 60_000L;
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute);
            }
            return existing;
        });
        int count = w.count.incrementAndGet();
        if (count > perMinute) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"rate limit exceeded\",\"limitPerMinute\":" + perMinute + "}");
            return;
        }
        // Opportunistically bound the map so it cannot grow unboundedly under a spoofed-IP flood.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        }
        chain.doFilter(request, response);
    }

    private static String clientKey(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
