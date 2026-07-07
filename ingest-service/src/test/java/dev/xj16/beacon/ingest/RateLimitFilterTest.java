package dev.xj16.beacon.ingest;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the fixed-window rate limiter. Uses Spring's mock servlet request/response (from
 * spring-test) so no server is needed.
 */
class RateLimitFilterTest {

    private MockHttpServletRequest apiRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/events");
        req.setRemoteAddr(ip);
        return req;
    }

    private int run(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> res.setStatus(200);
        filter.doFilter(apiRequest(ip), res, chain);
        return res.getStatus();
    }

    @Test
    void allowsUpToLimitThenReturns429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3);
        assertEquals(200, run(filter, "10.0.0.1"));
        assertEquals(200, run(filter, "10.0.0.1"));
        assertEquals(200, run(filter, "10.0.0.1"));
        assertEquals(429, run(filter, "10.0.0.1"), "4th request in the window should be throttled");
    }

    @Test
    void limitsArePerClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1);
        assertEquals(200, run(filter, "10.0.0.1"));
        assertEquals(429, run(filter, "10.0.0.1"));
        // A different IP has its own independent budget.
        assertEquals(200, run(filter, "10.0.0.2"));
    }

    @Test
    void zeroLimitDisablesThrottling() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(0);
        // shouldNotFilter short-circuits when disabled, so the chain always runs.
        assertTrue(filter.shouldNotFilter(apiRequest("10.0.0.1")));
    }

    @Test
    void nonApiPathsAreNotFiltered() {
        RateLimitFilter filter = new RateLimitFilter(1);
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");
        assertTrue(filter.shouldNotFilter(actuator));
    }

    @Test
    void throttledResponseSetsRetryAfterHeader() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1);
        run(filter, "10.0.0.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> res.setStatus(200);
        filter.doFilter(apiRequest("10.0.0.9"), res, chain);
        assertEquals(429, res.getStatus());
        assertEquals("60", res.getHeader("Retry-After"));
    }
}
