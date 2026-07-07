package dev.xj16.beacon.ingest;

import dev.xj16.beacon.common.LogEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Container-free web-layer tests for the ingest edge: validation, malformed-body handling, id
 * backfill, and the stats endpoint. Kafka and the consumer are mocked, so this needs no broker and
 * runs in the fast unit lane while still exercising the real validation + controller-advice wiring.
 */
@WebMvcTest(IngestController.class)
class IngestControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    KafkaTemplate<String, LogEvent> kafkaTemplate;

    @MockBean
    EventConsumer consumer;

    @Test
    void validEventIsAcceptedAndPublished() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"evt-1","service":"orders-api-prod","level":"ERROR",
                                 "message":"boom","attributes":{"status":500}}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.id").value("evt-1"));
        verify(kafkaTemplate).send(eq("beacon.events"), eq("evt-1"), any(LogEvent.class));
    }

    @Test
    void missingIdIsBackfilledNotRejected() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"service":"orders-api-prod","level":"INFO","message":"ok"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void blankServiceIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"service":"","level":"INFO","message":"ok"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation failed"))
                .andExpect(jsonPath("$.details").isArray());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void missingMessageIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"service":"orders-api-prod","level":"INFO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownLevelIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"service":"orders-api-prod","level":"LOUD","message":"hi"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonBodyIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed request body"));
    }

    @Test
    void statsReportsIndexedCount() throws Exception {
        when(consumer.indexedCount()).thenReturn(42L);
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(42));
    }
}
