package com.getconvoy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getconvoy.client.ApiClient;
import com.getconvoy.client.ApiException;
import com.getconvoy.client.JSON;
import com.getconvoy.models.DatastoreDeliveryAttempt;
import com.getconvoy.models.DeliveryAttemptList;
import com.getconvoy.models.GetDeliveryAttempts200Response;
import com.getconvoy.models.GetDeliveryAttempts200ResponseAllOfDataOneOf;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Offline coverage of both server response shapes, including typed array items. */
class DeliveryAttemptsApiContractTest {
    private static final String PATH = "/api/v1/projects/proj-1/eventdeliveries/delivery-1/deliveryattempts";
    private static final String ATTEMPTS = "[{\"uid\":\"attempt-1\",\"http_status\":\"200\"},"
            + "{\"uid\":\"attempt-2\",\"http_status\":\"500\"}]";

    private final ObjectMapper mapper = JSON.getDefault().getMapper();
    private final AtomicReference<String> responseData = new AtomicReference<>("[]");
    private final AtomicReference<URI> requestUri = new AtomicReference<>();
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> requestAuth = new AtomicReference<>();
    private HttpServer server;
    private DeliveryAttemptsApi api;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            requestMethod.set(exchange.getRequestMethod());
            requestAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"status\":true,\"message\":\"ok\",\"data\":"
                    + responseData.get() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        ApiClient client = new ApiClient();
        client.updateBaseUri("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
        client.setRequestInterceptor(b -> b.header("Authorization", "Bearer test-key"));
        api = new DeliveryAttemptsApi(client);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void defaultResponseIsATypedArrayAndOmitsOptionalQueryParameters() throws Exception {
        responseData.set(ATTEMPTS);
        GetDeliveryAttempts200Response response = api.getDeliveryAttempts("proj-1", "delivery-1", null, null);

        assertRequest();
        assertNull(requestUri.get().getRawQuery());
        DeliveryAttemptList attempts = assertInstanceOf(DeliveryAttemptList.class, response.getData().getActualInstance());
        assertEquals(2, attempts.size());
        assertInstanceOf(DatastoreDeliveryAttempt.class, attempts.get(0));
        assertEquals("attempt-1", attempts.get(0).getUid());
        assertEquals("attempt-2", attempts.get(1).getUid());
        assertEquals("500", attempts.get(1).getHttpStatus());
        assertEquals(attempts, response.getData().getDeliveryAttemptList());
        assertRoundTrip(response, true);
    }

    @Test
    void emptyDefaultResponseStaysAnArrayWithExplicitFalse() throws Exception {
        GetDeliveryAttempts200Response response = api.getDeliveryAttempts("proj-1", "delivery-1", false, null);

        assertRequest();
        assertEquals("paginated=false", requestUri.get().getRawQuery());
        assertTrue(response.getData().getDeliveryAttemptList().isEmpty());
        assertRoundTrip(response, true);
    }

    @Test
    void paginatedResponseKeepsTypedContentAndCursor() throws Exception {
        responseData.set("{\"content\":" + ATTEMPTS + ",\"next_cursor\":\"older-attempt\"}");
        GetDeliveryAttempts200Response response = api.getDeliveryAttempts("proj-1", "delivery-1", true, "cursor/with+symbols=");

        assertRequest();
        assertEquals("paginated=true&before=cursor%2Fwith%2Bsymbols%3D", requestUri.get().getRawQuery());
        GetDeliveryAttempts200ResponseAllOfDataOneOf page = assertInstanceOf(
                GetDeliveryAttempts200ResponseAllOfDataOneOf.class, response.getData().getActualInstance());
        assertEquals(2, page.getContent().size());
        assertInstanceOf(DatastoreDeliveryAttempt.class, page.getContent().get(0));
        assertEquals("attempt-1", page.getContent().get(0).getUid());
        assertEquals("older-attempt", page.getNextCursor());
        assertRoundTrip(response, false);
    }

    @Test
    void emptyLastPageKeepsEmptyCursorAndContent() throws Exception {
        responseData.set("{\"content\":[],\"next_cursor\":\"\"}");
        GetDeliveryAttempts200Response response = api.getDeliveryAttempts("proj-1", "delivery-1", true, null);

        assertRequest();
        assertEquals("paginated=true", requestUri.get().getRawQuery());
        GetDeliveryAttempts200ResponseAllOfDataOneOf page =
                response.getData().getGetDeliveryAttempts200ResponseAllOfDataOneOf();
        assertTrue(page.getContent().isEmpty());
        assertEquals("", page.getNextCursor());
        assertRoundTrip(response, false);
    }

    @Test
    void singleAttemptStillUsesTheDetailRoute() throws Exception {
        responseData.set("{\"uid\":\"attempt-1\"}");

        assertEquals("attempt-1", api.getDeliveryAttempt("proj-1", "delivery-1", "attempt-1").getData().getUid());
        assertEquals(PATH + "/attempt-1", requestUri.get().getPath());
        assertNull(requestUri.get().getRawQuery());
    }

    @Test
    void invalidScalarResponseIsRejected() {
        responseData.set("42");
        assertThrows(ApiException.class, () -> api.getDeliveryAttempts("proj-1", "delivery-1", null, null));
    }

    private void assertRequest() {
        assertEquals("GET", requestMethod.get());
        assertEquals(PATH, requestUri.get().getPath());
        assertEquals("Bearer test-key", requestAuth.get());
    }

    private void assertRoundTrip(GetDeliveryAttempts200Response response, boolean isArray) throws Exception {
        String serialized = mapper.writeValueAsString(response);
        JsonNode data = mapper.readTree(serialized).path("data");
        assertEquals(isArray, data.isArray());
        GetDeliveryAttempts200Response reparsed = mapper.readValue(serialized, GetDeliveryAttempts200Response.class);
        assertEquals(response.getData().getActualInstance(), reparsed.getData().getActualInstance());
        if (!isArray) {
            assertEquals(mapper.readTree(responseData.get()).path("next_cursor"), data.path("next_cursor"));
        }
    }
}
