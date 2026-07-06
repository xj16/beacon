package dev.xj16.beacon.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Fast, container-free tests for the query DTOs. These always run in CI regardless of whether a
 * Docker daemon is available for the Testcontainers integration tests.
 */
class DtoTest {

    @Test
    fun searchResponseCarriesHits() {
        val hit = EventHit(
            id = "1", service = "svc", level = "ERROR", severity = 4,
            message = "boom", timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            environment = "prod", anomalyScore = 0.8,
        )
        val response = SearchResponse(total = 1, hits = listOf(hit))
        assertEquals(1, response.total)
        assertEquals("boom", response.hits.first().message)
        assertEquals(0.8, response.hits.first().anomalyScore)
    }

    @Test
    fun aggregationResponseBuckets() {
        val aggs = AggregationResponse(
            total = 5,
            byService = listOf(Bucket("a", 3), Bucket("b", 2)),
            bySeverity = listOf(Bucket("4", 5)),
            byEnvironment = listOf(Bucket("prod", 5)),
        )
        assertEquals(5, aggs.total)
        assertEquals(5, aggs.byService.sumOf { it.count })
    }
}
