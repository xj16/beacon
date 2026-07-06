package dev.xj16.beacon.query

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Public query surface for Beacon.
 *
 * ```
 * GET /api/search?q=timeout&service=orders-api-prod&minSeverity=4&page=0&size=20
 * GET /api/aggregations?environment=prod
 * ```
 */
@RestController
@RequestMapping("/api")
class SearchController(private val searchService: SearchService) {

    @GetMapping("/search")
    fun search(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(required = false) service: String?,
        @RequestParam(required = false) environment: String?,
        @RequestParam(required = false) minSeverity: Int?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): SearchResponse = searchService.search(
        text = q,
        service = service,
        environment = environment,
        minSeverity = minSeverity,
        from = from?.let(Instant::parse),
        to = to?.let(Instant::parse),
        page = page.coerceAtLeast(0),
        size = size.coerceIn(1, 200),
    )

    @GetMapping("/aggregations")
    fun aggregations(
        @RequestParam(required = false) service: String?,
        @RequestParam(required = false) environment: String?,
        @RequestParam(required = false) minSeverity: Int?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): AggregationResponse = searchService.aggregate(
        service = service,
        environment = environment,
        minSeverity = minSeverity,
        from = from?.let(Instant::parse),
        to = to?.let(Instant::parse),
    )
}
