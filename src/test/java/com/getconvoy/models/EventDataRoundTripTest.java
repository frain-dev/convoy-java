package com.getconvoy.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getconvoy.client.JSON;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression test for arbitrary-JSON event payloads. The OpenAPI spec must
 * keep event {@code data} an open map ({@code additionalProperties: true},
 * frain-dev/convoy#2737); if a regeneration ever narrows it, payload keys
 * would be silently dropped and these assertions fail.
 */
class EventDataRoundTripTest {

    private final ObjectMapper mapper = JSON.getDefault().getMapper();

    @Test
    void outboundEventDataKeepsAllKeys() throws Exception {
        ModelsCreateEvent ev = new ModelsCreateEvent()
                .endpointId("ep-1")
                .eventType("invoice.paid")
                .data(Map.of(
                        "amount", 100,
                        "currency", "USD",
                        "nested", Map.of("customer", "cus_123")));

        String out = mapper.writeValueAsString(ev);

        assertTrue(out.contains("\"amount\":100"), out);
        assertTrue(out.contains("\"currency\":\"USD\""), out);
        assertTrue(out.contains("\"customer\":\"cus_123\""), out);
    }

    @Test
    void inboundEventDataKeepsAllKeys() throws Exception {
        String inbound = "{\"uid\":\"evt-1\",\"event_type\":\"invoice.paid\","
                + "\"data\":{\"amount\":100,\"nested\":{\"customer\":\"cus_123\"}}}";

        DatastoreEvent parsed = mapper.readValue(inbound, DatastoreEvent.class);

        Map<String, Object> data = parsed.getData();
        assertEquals(100, data.get("amount"));
        assertEquals(Map.of("customer", "cus_123"), data.get("nested"));
    }
}
