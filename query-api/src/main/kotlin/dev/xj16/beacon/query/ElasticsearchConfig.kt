package dev.xj16.beacon.query

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

/** Wires the Elasticsearch Java client from the configured URI. */
@Configuration
class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    fun restClient(@Value("\${beacon.elasticsearch.uri:http://localhost:9200}") uri: String): RestClient {
        val parsed = URI.create(uri)
        val host = parsed.host ?: "localhost"
        val port = if (parsed.port == -1) 9200 else parsed.port
        val scheme = parsed.scheme ?: "http"
        return RestClient.builder(HttpHost(host, port, scheme)).build()
    }

    @Bean
    fun elasticsearchClient(restClient: RestClient): ElasticsearchClient {
        val mapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
        val transport = RestClientTransport(restClient, JacksonJsonpMapper(mapper))
        return ElasticsearchClient(transport)
    }
}
