package dev.xj16.beacon.ingest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Builds the Elasticsearch Java client from the configured URI. The JSONP mapper reuses a Jackson
 * {@link ObjectMapper} that understands {@code java.time} types so {@code Instant}s serialize as
 * ISO-8601 dates rather than epoch arrays.
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient elasticsearchRestClient(
            @Value("${beacon.elasticsearch.uri:http://localhost:9200}") String uri) {
        URI parsed = URI.create(uri);
        int port = parsed.getPort() == -1 ? 9200 : parsed.getPort();
        String scheme = parsed.getScheme() == null ? "http" : parsed.getScheme();
        String host = parsed.getHost() == null ? "localhost" : parsed.getHost();
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        // Serialize Instant as ISO-8601 strings (not epoch floats) so the Elasticsearch `date`
        // mapping accepts them; a float timestamp triggers a mapper_parsing_exception on index.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper(mapper));
        return new ElasticsearchClient(transport);
    }
}
