package dev.xj16.beacon.query

import java.time.Instant

/** A single search hit returned to API clients. */
data class EventHit(
    val id: String?,
    val service: String?,
    val level: String?,
    val severity: Int?,
    val message: String?,
    val timestamp: Instant?,
    val environment: String?,
    val anomalyScore: Double?,
)

/** Paginated full-text search result. */
data class SearchResponse(
    val total: Long,
    val hits: List<EventHit>,
)

/** One bucket in a terms aggregation (e.g. count of events for a given service). */
data class Bucket(
    val key: String,
    val count: Long,
)

/** Aggregation summary: totals sliced by service, by severity, and by environment. */
data class AggregationResponse(
    val total: Long,
    val byService: List<Bucket>,
    val bySeverity: List<Bucket>,
    val byEnvironment: List<Bucket>,
)
