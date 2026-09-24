package com.food.delivery.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayHttpIntegrationTest {

    private static final HttpServer UPSTREAM = startUpstream();

    @LocalServerPort
    int gatewayPort;

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("CART_SERVICE_URI", () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
        registry.add("DELIVERY_SERVICE_URI", () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
        registry.add("GATEWAY_JWT_JWKS_URI", () -> "http://localhost/.well-known/jwks.json");
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void checkout_route_forwardsAuthenticatedRequest() {
        webTestClient.mutateWith(mockJwt())
                .post()
                .uri("/cart/checkout")
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.status").isEqualTo("PROCESSING");
    }

    @Test
    void assignments_route_forwardsAuthenticatedRequest() {
        webTestClient.mutateWith(mockJwt())
                .get()
                .uri("/assignments")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void openApiRoute_proxiesCartSpecification() {
        webTestClient.get()
                .uri("/openapi/cart")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.openapi").isEqualTo("3.0.3");
    }

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", GatewayHttpIntegrationTest::respondFromUpstream);
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void respondFromUpstream(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = path.equals("/v3/api-docs")
                ? "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"Cart API\"}}"
                : "{\"status\":\"PROCESSING\"}";
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", path.equals("/v3/api-docs")
                ? "application/json"
                : "application/json");
        exchange.sendResponseHeaders(path.equals("/cart/checkout") ? 202 : 200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
