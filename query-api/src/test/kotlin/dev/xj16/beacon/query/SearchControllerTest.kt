package dev.xj16.beacon.query

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Container-free web-layer tests for the query API. The [SearchService] is mocked, so these assert
 * the controller's own contract — parameter clamping, defaults, and response shapes — without a real
 * Elasticsearch. The full search/aggregation semantics are covered by [SearchServiceIT].
 */
@WebMvcTest(SearchController::class)
class SearchControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @MockBean
    lateinit var searchService: SearchService

    @Test
    fun `size is clamped to at most 200`() {
        whenever(
            searchService.search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
        ).thenReturn(SearchResponse(0, emptyList()))

        mvc.perform(get("/api/search?size=9999")).andExpect(status().isOk)

        // The controller must clamp the requested size (9999) to the 200 maximum.
        org.mockito.Mockito.verify(searchService)
            .search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(0), eq(200))
    }

    @Test
    fun `size is clamped to at least 1 and page to at least 0`() {
        whenever(
            searchService.search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
        ).thenReturn(SearchResponse(0, emptyList()))

        mvc.perform(get("/api/search?size=0&page=-5")).andExpect(status().isOk)

        org.mockito.Mockito.verify(searchService)
            .search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(0), eq(1))
    }

    @Test
    fun `empty result returns total 0 and empty hits`() {
        whenever(
            searchService.search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
        ).thenReturn(SearchResponse(0, emptyList()))

        mvc.perform(get("/api/search?q=nothing"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.hits").isArray)
            .andExpect(jsonPath("$.hits").isEmpty)
    }

    @Test
    fun `search passes through free-text and filters`() {
        whenever(
            searchService.search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
        ).thenReturn(
            SearchResponse(1, listOf(
                EventHit("1", "orders-api-prod", "ERROR", 4, "timeout", null, "prod", 0.9)
            ))
        )

        mvc.perform(get("/api/search?q=timeout&service=orders-api-prod&minSeverity=4&environment=prod"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.hits[0].service").value("orders-api-prod"))
            .andExpect(jsonPath("$.hits[0].anomalyScore").value(0.9))

        org.mockito.Mockito.verify(searchService)
            .search(eq("timeout"), eq("orders-api-prod"), eq("prod"), eq(4), anyOrNull(), anyOrNull(), eq(0), eq(20))
    }

    @Test
    fun `aggregations returns bucketed shape`() {
        whenever(
            searchService.aggregate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(
            AggregationResponse(
                total = 3,
                byService = listOf(Bucket("orders-api-prod", 2)),
                bySeverity = listOf(Bucket("4", 2), Bucket("2", 1)),
                byEnvironment = listOf(Bucket("prod", 3)),
            )
        )

        mvc.perform(get("/api/aggregations?environment=prod"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.byService[0].key").value("orders-api-prod"))
            .andExpect(jsonPath("$.byEnvironment[0].count").value(3))
    }
}
