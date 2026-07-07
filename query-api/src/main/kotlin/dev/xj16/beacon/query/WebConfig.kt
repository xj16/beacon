package dev.xj16.beacon.query

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS policy for the read API.
 *
 * The bundled dashboard is served from query-api's own origin, so no CORS is needed by default and
 * the API stays locked down (same-origin only). When the dashboard is hosted elsewhere — e.g.
 * embedded on a portfolio — set `BEACON_CORS_ALLOWED_ORIGINS` to a comma-separated allowlist to
 * grant those specific origins read access. Wildcard origins are deliberately not supported.
 */
@Configuration
class WebConfig(
    @Value("\${beacon.cors.allowed-origins:}") private val allowedOrigins: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (origins.isEmpty()) {
            return // same-origin only
        }
        registry.addMapping("/api/**")
            .allowedOrigins(*origins.toTypedArray())
            .allowedMethods("GET")
            .maxAge(3600)
    }
}
