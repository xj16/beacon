package dev.xj16.beacon.query

import co.elastic.clients.elasticsearch.ElasticsearchClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Integration test: index a handful of documents into a real Elasticsearch, then exercise the
 * [SearchService] full-text search and aggregations. Requires Docker; runs in CI.
 *
 * The Elasticsearch container is started in a static initializer (not via the JUnit `@Container`
 * extension) so its mapped port is available when Spring resolves `@DynamicPropertySource`.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchServiceIT {

    companion object {
        @JvmStatic
        val ES: ElasticsearchContainer =
            ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.14.3")
            )
                .withEnv("xpack.security.enabled", "false")
                .withEnv("discovery.type", "single-node")
                .withStartupTimeout(Duration.ofMinutes(3))
                .apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("beacon.elasticsearch.uri") { "http://" + ES.httpHostAddress }
        }
    }

    @Autowired
    lateinit var client: ElasticsearchClient

    @Autowired
    lateinit var searchService: SearchService

    @BeforeAll
    fun seed() {
        val docs = listOf(
            mapOf(
                "id" to "1", "service" to "orders-api-prod", "level" to "ERROR", "severity" to 4,
                "message" to "database connection timeout", "environment" to "prod",
                "timestamp" to "2026-01-01T00:00:00Z"
            ),
            mapOf(
                "id" to "2", "service" to "orders-api-prod", "level" to "INFO", "severity" to 2,
                "message" to "order placed successfully", "environment" to "prod",
                "timestamp" to "2026-01-01T00:01:00Z"
            ),
            mapOf(
                "id" to "3", "service" to "checkout-api-staging", "level" to "WARN", "severity" to 3,
                "message" to "payment gateway slow, retrying timeout", "environment" to "staging",
                "timestamp" to "2026-01-01T00:02:00Z"
            ),
        )
        docs.forEach { doc ->
            client.index { i -> i.index("beacon-events").id(doc["id"] as String).document(doc) }
        }
        client.indices().refresh { r -> r.index("beacon-events") }
    }

    @Test
    fun fullTextSearchMatchesMessage() {
        val result = searchService.search(
            text = "timeout", service = null, environment = null, minSeverity = null,
            from = null, to = null, page = 0, size = 10
        )
        // docs 1 and 3 mention "timeout"
        assertEquals(2, result.total)
        assertTrue(result.hits.all { it.message!!.contains("timeout") })
    }

    @Test
    fun filterByServiceAndSeverity() {
        val result = searchService.search(
            text = null, service = "orders-api-prod", environment = null, minSeverity = 4,
            from = null, to = null, page = 0, size = 10
        )
        assertEquals(1, result.total)
        assertEquals("1", result.hits.first().id)
    }

    @Test
    fun aggregationsBucketByService() {
        val aggs = searchService.aggregate(
            service = null, environment = null, minSeverity = null, from = null, to = null
        )
        assertEquals(3, aggs.total)
        val orders = aggs.byService.firstOrNull { it.key == "orders-api-prod" }
        assertEquals(2, orders?.count)
        assertTrue(aggs.bySeverity.isNotEmpty())
        assertTrue(aggs.byEnvironment.any { it.key == "prod" })
    }
}
