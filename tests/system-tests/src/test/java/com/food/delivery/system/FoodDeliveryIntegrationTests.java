package com.food.delivery.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class FoodDeliveryIntegrationTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodDeliveryIntegrationTests.class);

    static {
        log("Testcontainers Compose startup beginning");
    }

    @Container
    static final ComposeContainer environment = new ComposeContainer(findComposeFile())
            .withExposedService("api-gateway-1", 8080)
            .withExposedService("postgres-1", 5432);

    @RegisterExtension
    static final TestWatcher progressLogger = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            log("PASS  " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            log("FAIL  " + context.getDisplayName() + " - "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    };

    private static FoodDeliveryTestClient client;

    @BeforeEach
    void logTestStart(TestInfo testInfo) {
        log("START " + testInfo.getDisplayName());
    }

    private static File findComposeFile() {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            var candidate = directory.resolve("deploy").resolve("docker-compose.yml");
            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Could not locate deploy/docker-compose.yml");
    }

    @BeforeAll
    static void waitForStack() throws Exception {
        log("Testcontainers Compose startup complete; checking service readiness");
        var gatewayUrl = "http://" + environment.getServiceHost("api-gateway-1", 8080)
                + ":" + environment.getServicePort("api-gateway-1", 8080);
        client = new FoodDeliveryTestClient(gatewayUrl);
        client.awaitStackReady();
    }

    private static void log(String message) {
        LOGGER.info("[e2e] {} {}", Instant.now(), message);
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void checkoutCompletesDeliveryThroughKafkaWorkflow() throws Exception {
        var ownerToken = client.login("owner1", "admin123");
        var adminToken = client.login("admin", "admin123");
        var courier1Token = client.login("courier1", "admin123");
        var courier2Token = client.login("courier2", "admin123");
        var checkout = client.createOrder(1);
        assertThat(client.json(client.request("POST", "/cart/checkout", checkout.customerToken(), null, checkout.cartId()))
                .get("cartId").asText()).isEqualTo(checkout.cartId());

        client.await("kitchen ticket", "/ops/orders/" + checkout.orderId(), ownerToken,
                body -> "PENDING".equals(body.path("status").asText()));
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"ACCEPTED\",\"etaMinutes\":1}"), 200);
        client.awaitOrderStatus(checkout, "RESTAURANT_ACCEPTED");
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"IN_PROGRESS\"}"), 200);
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"READY\"}"), 200);

        client.awaitOrderStatus(checkout, "READY_FOR_PICKUP");
        var assignment = awaitOffer(checkout, adminToken, courier1Token, courier2Token);
        var assignmentId = assignment.assignmentId();
        var courierToken = assignment.courierToken();
        client.assertStatus(client.request("POST", "/assignments/" + assignmentId + "/accept", courierToken, null), 200);
        client.assertStatus(client.request("POST", "/assignments/" + assignmentId + "/pickup", courierToken, null), 200);
        client.awaitOrderStatus(checkout, "OUT_FOR_DELIVERY");
        client.assertStatus(client.request("POST", "/assignments/" + assignmentId + "/deliver", courierToken, null), 200);
        client.awaitOrderStatus(checkout, "DELIVERED");
    }

    @Test
    void customerCanApplyAndBeApprovedAsCourier() throws Exception {
        var applicant = client.registerUniqueCustomerCredentials();
        var adminToken = client.login("admin", "admin123");

        var application = client.request("POST", "/courier-applications", applicant.token(),
                client.jsonBody(Map.of("fullName", "Integration Courier", "phoneNumber", "+40123456789",
                        "vehicleInformation", "Bicycle", "operatingArea", "Bucharest")));
        client.assertStatus(application, 201);
        var applicationJson = client.json(application);
        var applicationId = applicationJson.get("id").asText();
        var userId = UUID.fromString(applicationJson.get("userId").asText());

        client.assertStatus(client.request("POST", "/courier-applications/" + applicationId + "/approve",
                adminToken, null), 200);

        try {
            awaitCourierStatus(userId, "AVAILABLE");
            var courierToken = client.login(applicant.username(), applicant.password());
            client.await("courier profile provisioning", "/assignments?active=true", courierToken,
                    body -> body.isArray());
        } finally {
            client.assertStatus(client.request("POST", "/couriers/" + userId + "/disable", adminToken,
                    client.jsonBody(Map.of("reason", "Integration test cleanup"))), 204);
            awaitCourierStatus(userId, "SUSPENDED");
        }
    }

    @Test
    void approvedCourierCanBeSuspendedAndRestored() throws Exception {
        var applicant = client.registerUniqueCustomerCredentials();
        var adminToken = client.login("admin", "admin123");

        var application = client.request("POST", "/courier-applications", applicant.token(),
                client.jsonBody(Map.of("fullName", "Suspension Test Courier", "phoneNumber", "+40123456790",
                        "vehicleInformation", "Bicycle", "operatingArea", "Bucharest")));
        client.assertStatus(application, 201);
        var applicationJson = client.json(application);
        var applicationId = applicationJson.get("id").asText();
        var userId = UUID.fromString(applicationJson.get("userId").asText());

        client.assertStatus(client.request("POST", "/courier-applications/" + applicationId + "/approve",
                adminToken, null), 200);

        var courierRoleActive = true;
        try {
            awaitCourierStatus(userId, "AVAILABLE");
            var enabledToken = client.login(applicant.username(), applicant.password());
            client.await("courier profile provisioning", "/assignments?active=true", enabledToken,
                    body -> body.isArray());

            client.assertStatus(client.request("POST", "/couriers/" + userId + "/disable", adminToken,
                    client.jsonBody(Map.of("reason", "Integration test suspension"))), 204);
            courierRoleActive = false;
            awaitCourierStatus(userId, "SUSPENDED");
            var suspendedToken = client.login(applicant.username(), applicant.password());
            client.assertStatus(client.request("GET", "/assignments?active=true", suspendedToken, null), 403);

            client.assertStatus(client.request("POST", "/couriers/" + userId + "/enable", adminToken, null), 204);
            courierRoleActive = true;
            awaitCourierStatus(userId, "AVAILABLE");
            var restoredToken = client.login(applicant.username(), applicant.password());
            client.await("courier role restoration", "/assignments?active=true", restoredToken,
                    body -> body.isArray());
        } finally {
            // Keep the temporary courier out of the shared seeded delivery pool.
            if (courierRoleActive) {
                client.assertStatus(client.request("POST", "/couriers/" + userId + "/disable", adminToken,
                        client.jsonBody(Map.of("reason", "Integration test cleanup"))), 204);
            }
            awaitCourierStatus(userId, "SUSPENDED");
        }
    }

    @Test
    void restaurantOwnerCanManageMenuSections() throws Exception {
        var ownerToken = client.login("owner1", "admin123");
        var anotherOwnerToken = client.login("owner2", "admin123");
        var sectionName = "Integration Section " + UUID.randomUUID();
        var earlierSectionName = "Earlier Integration Section " + UUID.randomUUID();
        var sectionPath = "/restaurants/" + FoodDeliveryTestClient.RESTAURANT_ID + "/menu/sections";
        var itemPath = "/restaurants/" + FoodDeliveryTestClient.RESTAURANT_ID + "/menu/items";
        String sectionId = null;
        String earlierSectionId = null;
        String menuItemId = null;

        try {
            var anonymousRestaurant = client.request("GET", "/restaurants/" + FoodDeliveryTestClient.RESTAURANT_ID,
                    null, null);
            client.assertStatus(anonymousRestaurant, 200);
            var restaurant = client.json(anonymousRestaurant);
            assertThat(restaurant.get("id").asText()).isEqualTo(FoodDeliveryTestClient.RESTAURANT_ID.toString());
            assertThat(restaurant.get("name").asText()).isEqualTo("Pizza Palace");

            var anonymousRestaurantList = client.request("GET", "/restaurants", null, null);
            client.assertStatus(anonymousRestaurantList, 200);
            assertThat(client.json(anonymousRestaurantList).path("content").findValuesAsText("id"))
                    .contains(FoodDeliveryTestClient.RESTAURANT_ID.toString());

            var anonymousMenu = client.request("GET", "/restaurants/" + FoodDeliveryTestClient.RESTAURANT_ID
                    + "/menu", null, null);
            client.assertStatus(anonymousMenu, 200);
            var menuItems = client.json(anonymousMenu).path("content");
            assertThat(menuItems.findValuesAsText("id"))
                    .contains(FoodDeliveryTestClient.MENU_ITEM_ID.toString());
            assertThat(menuItems.findValuesAsText("name")).contains("Margherita Pizza");

            var anonymousSections = client.request("GET", sectionPath, null, null);
            client.assertStatus(anonymousSections, 200);
            client.assertStatus(client.request("POST", sectionPath, null,
                    client.jsonBody(Map.of("name", sectionName, "displayOrder", 20))), 401);

            var created = client.request("POST", sectionPath, ownerToken,
                    client.jsonBody(Map.of("name", sectionName, "displayOrder", 20)));
            client.assertStatus(created, 200);
            sectionId = client.json(created).get("id").asText();

            var createdEarlier = client.request("POST", sectionPath, ownerToken,
                    client.jsonBody(Map.of("name", earlierSectionName, "displayOrder", 10)));
            client.assertStatus(createdEarlier, 200);
            earlierSectionId = client.json(createdEarlier).get("id").asText();

            var duplicate = client.request("POST", sectionPath, ownerToken,
                    client.jsonBody(Map.of("name", sectionName, "displayOrder", 30)));
            client.assertStatus(duplicate, 409);

            var crossOwnerUpdate = client.request("PUT", sectionPath + "/" + sectionId, anotherOwnerToken,
                    client.jsonBody(Map.of("name", sectionName + " Unauthorized", "displayOrder", 1)));
            client.assertStatus(crossOwnerUpdate, 400);

            var updated = client.request("PUT", sectionPath + "/" + sectionId, ownerToken,
                    client.jsonBody(Map.of("name", sectionName + " Updated", "displayOrder", 30)));
            client.assertStatus(updated, 200);
            var updatedSection = client.json(updated);
            assertThat(updatedSection.get("name").asText()).isEqualTo(sectionName + " Updated");
            assertThat(updatedSection.get("displayOrder").asInt()).isEqualTo(30);

            var listed = client.request("GET", sectionPath, null, null);
            client.assertStatus(listed, 200);
            var sections = client.json(listed);
            var listedIds = sections.findValuesAsText("id");
            var listedNames = sections.findValuesAsText("name");
            assertThat(listedIds).contains(sectionId, earlierSectionId);
            assertThat(listedNames).contains(sectionName + " Updated", earlierSectionName);
            assertThat(listedNames.indexOf(earlierSectionName))
                    .isLessThan(listedNames.indexOf(sectionName + " Updated"));

            var itemName = "Section Test Item " + UUID.randomUUID();
            var itemBody = Map.of("sectionId", sectionId, "name", itemName,
                    "description", "Temporary system test item", "price", new BigDecimal("12.34"),
                    "available", true, "version", 0);
            var createdItem = client.request("POST", itemPath, ownerToken, client.jsonBody(itemBody));
            client.assertStatus(createdItem, 200);
            var item = client.json(createdItem);
            menuItemId = item.get("id").asText();
            assertThat(item.get("sectionId").asText()).isEqualTo(sectionId);

            client.assertStatus(client.request("DELETE", sectionPath + "/" + sectionId, ownerToken, null), 409);
            client.assertStatus(client.request("DELETE", itemPath + "/" + menuItemId, ownerToken, null), 200);
            menuItemId = null;
            client.assertStatus(client.request("DELETE", sectionPath + "/" + sectionId, ownerToken, null), 200);
            sectionId = null;
            client.assertStatus(client.request("DELETE", sectionPath + "/" + earlierSectionId, ownerToken, null), 200);
            earlierSectionId = null;
        } finally {
            if (menuItemId != null) {
                client.request("DELETE", itemPath + "/" + menuItemId, ownerToken, null);
            }
            if (sectionId != null) {
                client.request("DELETE", sectionPath + "/" + sectionId, ownerToken, null);
            }
            if (earlierSectionId != null) {
                client.request("DELETE", sectionPath + "/" + earlierSectionId, ownerToken, null);
            }
        }
    }

    private static void awaitCourierStatus(UUID userId, String expectedStatus) throws Exception {
        var host = environment.getServiceHost("postgres-1", 5432);
        var port = environment.getServicePort("postgres-1", 5432);
        var jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/delivery";
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        String lastStatus = "missing";
        SQLException lastError = null;

        while (System.nanoTime() < deadline) {
            try (var connection = DriverManager.getConnection(jdbcUrl, "postgres", "postgres");
                 var statement = connection.prepareStatement("SELECT status FROM couriers WHERE user_id = ?")) {
                statement.setObject(1, userId);
                try (var result = statement.executeQuery()) {
                    if (result.next()) {
                        lastStatus = result.getString("status");
                        if (expectedStatus.equals(lastStatus)) {
                            return;
                        }
                    }
                }
            } catch (SQLException e) {
                lastError = e;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Timed out waiting for courier " + userId + " status " + expectedStatus
                + "; last status=" + lastStatus, lastError);
    }

    @Test
    void paymentFailureMarksOrderAsFailed() throws Exception {
        var checkout = client.createOrder(8);
        var order = client.awaitOrderStatus(checkout, "PAYMENT_FAILED");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("103.92"));
    }

    @Test
    void restaurantRejectionMarksOrderAndTicketAsRejected() throws Exception {
        var ownerToken = client.login("owner1", "admin123");
        var checkout = client.createOrder(1);
        client.await("kitchen ticket", "/ops/orders/" + checkout.orderId(), ownerToken,
                body -> "PENDING".equals(body.path("status").asText()));
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"REJECTED\",\"reason\":\"System test rejection\"}"), 200);
        client.awaitOrderStatus(checkout, "RESTAURANT_REJECTED");
        client.await("rejected kitchen ticket", "/ops/orders/" + checkout.orderId(), ownerToken,
                body -> "REJECTED".equals(body.path("status").asText()));
    }

    @Test
    void cancellationAfterPaymentAuthorizationCancelsOrder() throws Exception {
        var checkout = client.createOrder(1);
        client.awaitOrderStatus(checkout, "PAYMENT_AUTHORIZED");
        client.assertStatus(client.request("PATCH", "/orders/" + checkout.orderId() + "/cancellation", checkout.customerToken(), null), 200);
        client.awaitOrderStatus(checkout, "CANCELLED");
    }

    @Test
    void lateCancellationChargesFeeAndCancelsDelivery() throws Exception {
        var ownerToken = client.login("owner1", "admin123");
        var adminToken = client.login("admin", "admin123");
        var courier1Token = client.login("courier1", "admin123");
        var courier2Token = client.login("courier2", "admin123");
        var checkout = client.createOrder(1);
        client.await("kitchen ticket", "/ops/orders/" + checkout.orderId(), ownerToken,
                body -> "PENDING".equals(body.path("status").asText()));
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"ACCEPTED\",\"etaMinutes\":1}"), 200);
        client.awaitOrderStatus(checkout, "RESTAURANT_ACCEPTED");
        var assignment = awaitOffer(checkout, adminToken, courier1Token, courier2Token);
        client.assertStatus(client.request("POST", "/assignments/" + assignment.assignmentId() + "/accept", assignment.courierToken(), null), 200);
        client.assertStatus(client.request("PATCH", "/orders/" + checkout.orderId() + "/cancellation", checkout.customerToken(), null), 200);
        client.awaitOrderStatus(checkout, "CANCELLED");
        client.awaitFirst("cancelled delivery", "/assignments?orderId=" + checkout.orderId(), assignment.courierToken(),
                body -> "CANCELLED".equals(body.path("status").asText()));
    }

    @Test
    void rejectedCourierOfferCreatesHistoricalReassignment() throws Exception {
        var ownerToken = client.login("owner1", "admin123");
        var adminToken = client.login("admin", "admin123");
        var courier1Token = client.login("courier1", "admin123");
        var courier2Token = client.login("courier2", "admin123");
        var checkout = client.createOrder(1);
        client.await("kitchen ticket", "/ops/orders/" + checkout.orderId(), ownerToken,
                body -> "PENDING".equals(body.path("status").asText()));
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"ACCEPTED\",\"etaMinutes\":1}"), 200);
        client.awaitOrderStatus(checkout, "RESTAURANT_ACCEPTED");

        var firstOffer = client.awaitFirst("first delivery offer", "/assignments?orderId=" + checkout.orderId(), adminToken,
                body -> "OFFERED".equals(body.path("status").asText()));
        var firstAssignmentId = firstOffer.get("assignmentId").asText();
        client.assertStatus(client.request("GET", "/assignments/" + firstAssignmentId, adminToken, null), 200);
        client.assertStatus(client.request("POST", "/assignments/" + firstAssignmentId + "/accept", adminToken, null), 403);
        var firstCourierToken = firstOffer.get("courierId").asText().endsWith("0020") ? courier1Token : courier2Token;
        client.assertStatus(client.request("POST", "/assignments/" + firstAssignmentId + "/reject", firstCourierToken, null), 200);

        var secondOffer = client.awaitFirst("replacement delivery offer", "/assignments?orderId=" + checkout.orderId(), adminToken,
                body -> "OFFERED".equals(body.path("status").asText())
                        && !firstAssignmentId.equals(body.path("assignmentId").asText()));
        var secondCourierToken = secondOffer.get("courierId").asText().endsWith("0020") ? courier1Token : courier2Token;
        client.assertStatus(client.request("POST", "/assignments/" + secondOffer.get("assignmentId").asText() + "/accept", secondCourierToken, null), 200);

        var history = client.request("GET", "/assignments?orderId=" + checkout.orderId(), adminToken, null);
        client.assertStatus(history, 200);
        var assignmentHistory = client.json(history);
        assertThat(assignmentHistory).hasSize(2);
        assertAssignmentStatus(assignmentHistory, "REJECTED");
        client.assertStatus(client.request("PATCH", "/orders/" + checkout.orderId() + "/cancellation", checkout.customerToken(), null), 200);
        client.awaitOrderStatus(checkout, "CANCELLED");
    }

    @Test
    void expiredCourierOfferCreatesReplacementAssignment() throws Exception {
        var ownerToken = client.login("owner1", "admin123");
        var adminToken = client.login("admin", "admin123");
        var courier1Token = client.login("courier1", "admin123");
        var courier2Token = client.login("courier2", "admin123");
        var checkout = client.createOrder(1);
        client.await("kitchen ticket", "/ops/orders/" + checkout.orderId(), ownerToken,
                body -> "PENDING".equals(body.path("status").asText()));
        client.assertStatus(client.request("PATCH", "/ops/orders/" + checkout.orderId() + "/status", ownerToken,
                "{\"status\":\"ACCEPTED\",\"etaMinutes\":1}"), 200);
        client.awaitOrderStatus(checkout, "RESTAURANT_ACCEPTED");

        var firstOffer = client.awaitFirst("first delivery offer", "/assignments?orderId=" + checkout.orderId(), adminToken,
                body -> "OFFERED".equals(body.path("status").asText()));
        var secondOffer = client.awaitFirst("offer after expiry", "/assignments?orderId=" + checkout.orderId(), adminToken,
                body -> "OFFERED".equals(body.path("status").asText())
                        && !firstOffer.get("assignmentId").asText().equals(body.path("assignmentId").asText()));
        var courierToken = secondOffer.get("courierId").asText().endsWith("0020") ? courier1Token : courier2Token;
        client.assertStatus(client.request("POST", "/assignments/" + secondOffer.get("assignmentId").asText() + "/accept", courierToken, null), 200);

        var history = client.request("GET", "/assignments?orderId=" + checkout.orderId(), adminToken, null);
        client.assertStatus(history, 200);
        assertAssignmentStatus(client.json(history), "EXPIRED");
        client.assertStatus(client.request("PATCH", "/orders/" + checkout.orderId() + "/cancellation", checkout.customerToken(), null), 200);
        client.awaitOrderStatus(checkout, "CANCELLED");
    }

    @Test
    void checkoutIsIdempotentAndOrderIsPrivateToCustomer() throws Exception {
        var checkout = client.createOrder(1);
        var repeated = client.request("POST", "/cart/checkout", checkout.customerToken(), null, checkout.cartId());
        client.assertStatus(repeated, 202);
        assertThat(client.json(repeated).get("cartId").asText()).isEqualTo(checkout.cartId());

        var anotherCustomer = client.registerUniqueCustomer();
        client.assertStatus(client.request("GET", "/orders/" + checkout.orderId(), anotherCustomer, null), 404);
        client.awaitOrderStatus(checkout, "PAYMENT_AUTHORIZED");
        client.assertStatus(client.request("PATCH", "/orders/" + checkout.orderId() + "/cancellation", checkout.customerToken(), null), 200);
        client.awaitOrderStatus(checkout, "CANCELLED");
    }

    private DeliveryOffer awaitOffer(FoodDeliveryTestClient.Checkout checkout, String adminToken,
                                     String courier1Token, String courier2Token) throws Exception {
        var offer = client.awaitFirst("delivery offer", "/assignments?orderId=" + checkout.orderId(), adminToken,
                body -> "OFFERED".equals(body.path("status").asText()));
        var courierToken = offer.get("courierId").asText().endsWith("0020") ? courier1Token : courier2Token;
        return new DeliveryOffer(offer.get("assignmentId").asText(), courierToken);
    }

    private void assertAssignmentStatus(JsonNode assignments, String expectedStatus) {
        var matchingAssignment = false;
        for (var assignment : assignments) {
            if (expectedStatus.equals(assignment.path("status").asText())) {
                matchingAssignment = true;
                break;
            }
        }
        assertThat(matchingAssignment)
                .as("Expected an assignment with status %s", expectedStatus)
                .isTrue();
    }

    private record DeliveryOffer(String assignmentId, String courierToken) {
    }
}
