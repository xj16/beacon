package dev.xj16.beacon.query

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.json.JsonData
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Query logic over the Elasticsearch `beacon-events` index.
 *
 * Provides:
 *  - [search]: full-text search over `message` with optional filters (service, environment,
 *    minimum severity, time range), sorted newest-first, paginated.
 *  - [aggregate]: terms aggregations bucketing events by service, severity, and environment.
 */
@Service
class SearchService(
    private val client: ElasticsearchClient,
    @Value("\${beacon.elasticsearch.index:beacon-events}") private val index: String,
) {

    fun search(
        text: String?,
        service: String?,
        environment: String?,
        minSeverity: Int?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
    ): SearchResponse {
        val filters = buildFilters(text, service, environment, minSeverity, from, to)

        val query = Query.of { q ->
            q.bool { b -> b.filter(filters) }
        }

        val response = client.search({ s ->
            s.index(index)
                .query(query)
                .from(page * size)
                .size(size)
                .sort { sort -> sort.field { f -> f.field("timestamp").order(SortOrder.Desc) } }
        }, Map::class.java)

        val hits = response.hits().hits().map { hit ->
            @Suppress("UNCHECKED_CAST")
            toHit(hit.source() as? Map<String, Any?>)
        }
        val total = response.hits().total()?.value() ?: hits.size.toLong()
        return SearchResponse(total = total, hits = hits)
    }

    fun aggregate(
        service: String?,
        environment: String?,
        minSeverity: Int?,
        from: Instant?,
        to: Instant?,
    ): AggregationResponse {
        val filters = buildFilters(null, service, environment, minSeverity, from, to)
        val query = Query.of { q -> q.bool { b -> b.filter(filters) } }

        val response = client.search({ s ->
            s.index(index)
                .size(0)
                .query(query)
                .aggregations("by_service", termsAgg("service"))
                .aggregations("by_severity", termsAgg("severity"))
                .aggregations("by_environment", termsAgg("environment"))
        }, Void::class.java)

        val total = response.hits().total()?.value() ?: 0L
        return AggregationResponse(
            total = total,
            byService = stringBuckets(response, "by_service"),
            bySeverity = longBuckets(response, "by_severity"),
            byEnvironment = stringBuckets(response, "by_environment"),
        )
    }

    // --- helpers -------------------------------------------------------------------------

    private fun termsAgg(field: String): Aggregation =
        Aggregation.of { a -> a.terms { t -> t.field(field).size(20) } }

    private fun buildFilters(
        text: String?,
        service: String?,
        environment: String?,
        minSeverity: Int?,
        from: Instant?,
        to: Instant?,
    ): List<Query> {
        val filters = mutableListOf<Query>()

        if (!text.isNullOrBlank()) {
            filters += Query.of { q -> q.match { m -> m.field("message").query(text) } }
        }
        if (!service.isNullOrBlank()) {
            filters += Query.of { q -> q.term { t -> t.field("service").value(service) } }
        }
        if (!environment.isNullOrBlank()) {
            filters += Query.of { q -> q.term { t -> t.field("environment").value(environment) } }
        }
        if (minSeverity != null) {
            filters += Query.of { q ->
                q.range { r ->
                    r.field("severity").gte(JsonData.of(minSeverity))
                }
            }
        }
        if (from != null || to != null) {
            filters += Query.of { q ->
                q.range { r ->
                    r.field("timestamp")
                    from?.let { r.gte(JsonData.of(it.toString())) }
                    to?.let { r.lte(JsonData.of(it.toString())) }
                    r
                }
            }
        }
        return filters
    }

    private fun toHit(source: Map<String, Any?>?): EventHit {
        if (source == null) {
            return EventHit(null, null, null, null, null, null, null, null)
        }
        return EventHit(
            id = source["id"] as? String,
            service = source["service"] as? String,
            level = source["level"] as? String,
            severity = (source["severity"] as? Number)?.toInt(),
            message = source["message"] as? String,
            timestamp = (source["timestamp"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() },
            environment = source["environment"] as? String,
            anomalyScore = (source["anomaly_score"] as? Number)?.toDouble(),
        )
    }

    private fun stringBuckets(response: co.elastic.clients.elasticsearch.core.SearchResponse<*>, name: String): List<Bucket> =
        response.aggregations()[name]?.sterms()?.buckets()?.array()
            ?.map { Bucket(key = it.key().stringValue(), count = it.docCount()) }
            ?: emptyList()

    private fun longBuckets(response: co.elastic.clients.elasticsearch.core.SearchResponse<*>, name: String): List<Bucket> =
        response.aggregations()[name]?.lterms()?.buckets()?.array()
            ?.map { Bucket(key = it.key().toString(), count = it.docCount()) }
            ?: emptyList()
}
