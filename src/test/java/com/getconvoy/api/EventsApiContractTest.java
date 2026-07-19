package com.getconvoy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.getconvoy.client.ApiClient;
import com.getconvoy.client.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Offline route-contract test: proves the generated client sends the verb,
 * path, auth header, and JSON body the Convoy server expects, using the JDK's
 * built-in HTTP server (no live instance, no extra dependencies).
 */
class EventsApiContractTest {

    // Plain class, not a record: the SDK compiles with -source 11.
    private static final class Captured {
        final String method;
        final String path;
        final String auth;
        final String contentType;
        final String body;

        Captured(String method, String path, String auth, String contentType, String body) {
            this.method = method;
            this.path = path;
            this.auth = auth;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static HttpServer server;
    private static ApiClient client;
    private static final AtomicReference<Captured> captured = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            captured.set(new Captured(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] resp = "{\"status\":true,\"message\":\"ok\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        client = new ApiClient();
        client.updateBaseUri("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        client.setRequestInterceptor(b -> b.header("Authorization", "Bearer test-key"));
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void createEndpointEventSendsExpectedRequest() throws ApiException {
        new EventsApi(client).createEndpointEvent("proj-1",
                new com.getconvoy.models.ModelsCreateEvent()
                        .endpointId("ep-1")
                        .eventType("invoice.paid")
                        .data(Map.of("amount", 100)));

        Captured req = captured.get();
        assertEquals("POST", req.method);
        assertEquals("/api/v1/projects/proj-1/events", req.path);
        assertEquals("Bearer test-key", req.auth);
        assertEquals("application/json", req.contentType);
        assertTrue(req.body.contains("\"endpoint_id\":\"ep-1\""), req.body);
        assertTrue(req.body.contains("\"event_type\":\"invoice.paid\""), req.body);
        assertTrue(req.body.contains("\"amount\":100"), req.body);
    }
}
