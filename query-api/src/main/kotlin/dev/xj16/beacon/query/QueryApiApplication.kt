package dev.xj16.beacon.query

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Beacon query API.
 *
 * A Kotlin Spring Boot service that sits in front of Elasticsearch and exposes ergonomic,
 * typed endpoints for full-text search over log messages and aggregations (counts by service,
 * by severity, error-rate over time).
 */
@SpringBootApplication
class QueryApiApplication

fun main(args: Array<String>) {
    runApplication<QueryApiApplication>(*args)
}
