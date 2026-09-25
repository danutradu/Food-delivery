package com.food.delivery.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class FoodDeliveryTestClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodDeliveryTestClient.class);
    static final UUID RESTAURANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    static final UUID MENU_ITEM_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");

    private final String gatewayUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    FoodDeliveryTestClient(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    void awaitStackReady() throws Exception {
        var deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        log("waiting for the gateway to become healthy at " + gatewayUrl);
        awaitServiceReady(deadline);
        // Actuator health can be UP shortly before Kafka listeners and the
        // scheduled outbox publishers have completed their cold start.
        // Actuator health can be UP before Kafka listener groups and the
        // scheduled outbox publishers have completed their cold start.
        // Leave enough time for the first event to be consumed reliably.
        log("all services are healthy; waiting 15s for Kafka/outbox settling");
        Thread.sleep(15000);
        log("stack is ready for end-to-end tests");
    }

    private void awaitServiceReady(long deadline) throws Exception {
        while (System.nanoTime() < deadline) {
            try {
                var response = httpClient.send(HttpRequest.newBuilder()
                        .uri(URI.create(gatewayUrl + "/actuator/health"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    log("gateway is healthy");
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Gateway did not become ready within 5 minutes");
    }

    private static void log(String message) {
        LOGGER.info("[e2e] {} {}", Instant.now(), message);
    }

    @Override
    public void close() {
        httpClient.close();
    }

    Checkout createOrder(int quantity) throws Exception {
        var customerToken = registerUniqueCustomer();
        var addItem = request("POST", "/cart/items", customerToken,
                "{\"restaurantId\":\"" + RESTAURANT_ID + "\",\"menuItemId\":\"" + MENU_ITEM_ID + "\",\"quantity\":" + quantity + "}");
        assertStatus(addItem, 200);
        var cartId = json(addItem).get("cartId").asText();
        var checkout = request("POST", "/cart/checkout", customerToken, null, cartId);
        assertStatus(checkout, 202);
        var order = await("order creation", "/orders/by-cart/" + cartId, customerToken, body -> body.hasNonNull("orderId"));
        return new Checkout(customerToken, cartId, order.get("orderId").asText(), checkout.body());
    }

    JsonNode awaitOrderStatus(Checkout checkout, String status) throws Exception {
        return await("order status " + status, "/orders/by-cart/" + checkout.cartId(), checkout.customerToken(),
                body -> status.equals(body.path("status").asText()));
    }

    JsonNode awaitFirst(String description, String path, String token, Predicate<JsonNode> complete) throws Exception {
        var body = await(description, path, token,
                response -> response.isArray() && firstMatching(response, complete) != null);
        return firstMatching(body, complete);
    }

    private JsonNode firstMatching(JsonNode body, Predicate<JsonNode> complete) {
        for (var item : body) {
            if (complete.test(item)) {
                return item;
            }
        }
        return null;
    }

    JsonNode await(String description, String path, String token, Predicate<JsonNode> complete) throws Exception {
        // CI runners are substantially slower during a cold Compose startup.
        // Keep the assertion bounded, but allow the asynchronous Kafka/outbox
        // workflow enough time to settle on a slower CI runner.
        var deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        HttpResponse<String> lastResponse = null;
        while (System.nanoTime() < deadline) {
            lastResponse = request("GET", path, token, null);
            if (lastResponse.statusCode() == 200) {
                var body = json(lastResponse);
                if (complete.test(body)) {
                    return body;
                }
            } else {
                assertThat(lastResponse.statusCode()).isEqualTo(404);
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for " + description + "; last response="
                + (lastResponse == null ? "none" : lastResponse.statusCode() + " " + lastResponse.body()));
    }

    JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    String jsonBody(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    void assertStatus(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
    }

    String login(String username, String password) throws Exception {
        var response = request("POST", "/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertStatus(response, 200);
        return json(response).get("accessToken").asText();
    }

    String registerUniqueCustomer() throws Exception {
        return registerUniqueCustomerCredentials().token();
    }

    RegisteredCustomer registerUniqueCustomerCredentials() throws Exception {
        var username = "integration_" + UUID.randomUUID().toString().replace("-", "");
        var response = request("POST", "/auth/register", null,
                "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"p@ssw0rd\"}");
        assertStatus(response, 200);
        return new RegisteredCustomer(username, "p@ssw0rd", json(response).get("accessToken").asText());
    }

    HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        return request(method, path, token, body, null);
    }

    HttpResponse<String> request(String method, String path, String token, String body, String idempotencyKey) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    record Checkout(String customerToken, String cartId, String orderId, String checkoutBody) {
    }

    record RegisteredCustomer(String username, String password, String token) {}
}
