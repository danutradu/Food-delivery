# Food Delivery – Microservices Demo

A reference microservices implementation of a food delivery platform built with **Java 21**, **Spring Boot 3.5.16**, **Apache Kafka + Avro**, **PostgreSQL**, and **RSA-signed JWTs with JWKS**.

## Stack

| Technology | Usage |
|---|---|
| Java 21 | All services |
| Spring Boot 3.5.16 | Web, Security, JPA, Kafka |
| Apache Kafka (KRaft) | Async event bus |
| Confluent Schema Registry | Avro schema management |
| Avro 1.12.2 | Strongly-typed events (`uuid` → `UUID`, `timestamp-millis` → `Instant`) |
| PostgreSQL 16 | Isolated databases for stateful services |
| RSA-signed JWT + JWKS | Auth-service issues tokens and publishes its public key; other services are resource servers |
| Lombok | Boilerplate reduction |
| Outbox pattern | Reliable event publishing (order-service, cart-service, etc.) |
| String Kafka keys | UUID keys are serialized consistently with `UUID.toString()` |

---

## Architecture

```
food-delivery/
├── common/
│   ├── common-avro       # Avro schemas (.avsc) + generated SpecificRecords
│   └── common-utils      # Shared: OutboxService, OutboxPublisher, OutboxEventUpdater
├── services/
│   ├── auth-service            # Register/login, issues JWT, emits UserRegisteredV1
│   ├── user-service            # Builds user profile from UserRegisteredV1
│   ├── catalog-service         # Restaurants & menu items, emits catalog events
│   ├── cart-service            # Cart management, emits CartCheckedOutV1
│   ├── order-service           # Order saga orchestrator (outbox pattern)
│   ├── payment-service         # Simulated payment gateway
│   ├── restaurant-ops-service  # Kitchen ticket management
│   ├── delivery-service        # Courier lifecycle, pending queue dispatch & delivery tracking
│   ├── notification-service    # Logs lifecycle events
│   └── api-gateway             # Routes client-facing HTTP APIs and OpenAPI documents
├── tests/system-tests          # Opt-in full-stack HTTP and Kafka workflow tests
└── deploy/
    ├── docker-compose.yml      # Full local stack: infrastructure, services, and gateway
    └── postgres-init/init.sql  # Database creation only; schemas and seed data use Liquibase
```

## Service Ports

| Service | Port | Database |
|---|---|---|
| api-gateway | 8080 | — |
| auth-service | 8082 | `auth` |
| order-service | 8083 | `orders` |
| payment-service | 8084 | `payments` |
| catalog-service | 8085 | `catalog` |
| cart-service | 8086 | `cart` |
| restaurant-ops-service | 8087 | `restaurant_ops` |
| delivery-service | 8088 | `delivery` |
| notification-service | 8089 | — |
| user-service | 8090 | `users_profile` |

## Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| `fd.user.registered.v1` | auth-service | user-service |
| `fd.user.courier-role-granted.v1` | auth-service | delivery-service |
| `fd.user.courier-role-revoked.v1` | auth-service | delivery-service |
| `fd.catalog.menu-item-created.v1` | catalog-service | cart-service |
| `fd.catalog.menu-item-updated.v1` | catalog-service | cart-service |
| `fd.catalog.menu-item-deleted.v1` | catalog-service | cart-service |
| `fd.cart.checked-out.v1` | cart-service | order-service |
| `fd.order.created.v1` | order-service | notification-service |
| `fd.order.cancelled.v1` | order-service | restaurant-ops-service, delivery-service, notification-service |
| `fd.payment.requested.v1` | order-service | payment-service |
| `fd.payment.authorized.v1` | payment-service | order-service, notification-service |
| `fd.payment.failed.v1` | payment-service | order-service, notification-service |
| `fd.payment.refund-requested.v1` | order-service | payment-service |
| `fd.payment.refund-completed.v1` | payment-service | notification-service |
| `fd.payment.fee-requested.v1` | order-service | payment-service |
| `fd.payment.fee-charged.v1` | payment-service | order-service, notification-service |
| `fd.payment.fee-failed.v1` | payment-service | order-service, notification-service |
| `fd.restaurant.acceptance-requested.v1` | order-service | restaurant-ops-service |
| `fd.restaurant.accepted.v1` | restaurant-ops-service | order-service, notification-service |
| `fd.restaurant.rejected.v1` | restaurant-ops-service | order-service, notification-service |
| `fd.restaurant.order-ready.v1` | restaurant-ops-service | order-service |
| `fd.delivery.requested.v1` | order-service | delivery-service |
| `fd.delivery.courier-assigned.v1` | delivery-service | notification-service |
| `fd.delivery.picked-up.v1` | delivery-service | order-service, notification-service |
| `fd.delivery.delivered.v1` | delivery-service | order-service, notification-service |

---

## Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

---

## Quick Start

### 1. Build JARs
```bash
mvn -DskipTests -q package
```

### 2. Start the full stack
```bash
cd deploy
docker compose up -d --build
```

Each stateful service runs its Liquibase changelog at startup. Liquibase creates the service-owned schema and initial demo data; Hibernate is configured with `ddl-auto: validate`, so it verifies mappings without changing the database.

To recreate the local databases from scratch:

```bash
docker compose down -v
docker compose up -d --build
```

The `-v` option deletes local development data.

Client-facing APIs for auth, catalog, cart, orders, restaurant operations, and courier assignments are reachable through the API gateway on `http://localhost:8080`. Other services communicate through Kafka and remain accessible on their direct development ports.

| Service | URL |
|---|---|
| API Gateway / Swagger | `http://localhost:8080` / `http://localhost:8080/swagger-ui.html` |
| Postgres | `localhost:5432` (postgres/postgres) |
| Kafka | `localhost:9094` (host) / `kafka:9092` (Compose network) |
| Schema Registry | `http://localhost:8081` |
| Kafdrop | `http://localhost:9000` |

### 3. Run services locally instead

Start the infrastructure from `deploy/`:

```bash
cd deploy
docker compose up -d postgres kafka schema-registry kafdrop
```

Then build from the repository root:

```bash
mvn -U -DskipTests clean install
```

Run each application in a separate terminal from the repository root:

```bash
mvn -f services/auth-service/pom.xml spring-boot:run
mvn -f services/user-service/pom.xml spring-boot:run
mvn -f services/catalog-service/pom.xml spring-boot:run
mvn -f services/cart-service/pom.xml spring-boot:run
mvn -f services/order-service/pom.xml spring-boot:run
mvn -f services/payment-service/pom.xml spring-boot:run
mvn -f services/restaurant-ops-service/pom.xml spring-boot:run
mvn -f services/delivery-service/pom.xml spring-boot:run
mvn -f services/notification-service/pom.xml spring-boot:run
mvn -f services/api-gateway/pom.xml spring-boot:run
```

---

## Configuration

Services use `application.yml` with environment variable overrides. Commonly used overrides include:

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Kafka broker |
| `SCHEMA_REGISTRY_URL` | `http://localhost:8081` | Confluent Schema Registry |
| `DB_USERNAME` | `postgres` | Postgres username |
| `DB_PASSWORD` | `postgres` | Postgres password |
| `JWT_JWKS_URI` | `http://localhost:8082/.well-known/jwks.json` | JWKS endpoint used by backend resource servers |
| `GATEWAY_JWT_JWKS_URI` | `http://localhost:8082/.well-known/jwks.json` | JWKS endpoint used by the API gateway |
| `CATALOG_SERVICE_URL` | `http://localhost:8085` | Used by cart-service to seed menu cache |
| `KAFKA_LISTENER_CONCURRENCY` | `3` | Listener concurrency; Compose overrides it to `1` for each service |
| `OUTBOX_MAX_RETRIES` | `5` | Outbox retry limit in cart, catalog, and order services |
| `OUTBOX_BATCH_SIZE` | `50` | Outbox polling batch size in cart and catalog services |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Comma-separated origins accepted by the gateway |
| `CORS_ALLOW_CREDENTIALS` | `false` | Whether gateway CORS responses allow credentials |
| `RATE_LIMIT_MAX_REQUESTS` | `100` | Maximum gateway requests per client and window; Compose overrides it to `1000` |
| `RATE_LIMIT_WINDOW` | `PT1M` | Gateway rate-limit window as an ISO-8601 duration |
| `*_SERVICE_URI` | Service-specific local URL | Gateway upstream URI, such as `ORDER_SERVICE_URI` |

---

## End-to-End Walkthrough

### 1. Login as a seeded demo user

Liquibase seeds the following demo accounts (all with password `admin123`):

| Username | Role(s) |
|----------|---------|
| `customer1` | CUSTOMER |
| `owner1` | RESTAURANT_OWNER (owner of Pizza Palace) |
| `owner2` | RESTAURANT_OWNER (owner of Burger Barn) |
| `courier1` | COURIER |
| `courier2` | COURIER |
| `admin` | ADMIN |

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"admin123"}'

CUSTOMER_TOKEN="<JWT from above>"

curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"owner1","password":"admin123"}'

OWNER_TOKEN="<JWT from above>"

curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"courier1","password":"admin123"}'

COURIER_TOKEN="<JWT from above>"

curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

ADMIN_TOKEN="<JWT from above>"
```

`POST /auth/register` creates customers. Use the seeded owner, courier, and admin accounts for privileged workflows.

### 2. Apply for courier access and manage courier roles

A customer can submit one active courier application. Admins can review pending applications, approve or reject them, and disable or re-enable an approved courier.

```bash
curl -X POST http://localhost:8080/courier-applications \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Jane Doe","phoneNumber":"+40123456789","vehicleInformation":"Bicycle","operatingArea":"Bucharest"}'

APPLICATION_ID="<application id from above>"

curl "http://localhost:8080/courier-applications?status=PENDING" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

curl -X POST "http://localhost:8080/courier-applications/$APPLICATION_ID/approve" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

curl -X POST "http://localhost:8080/couriers/<USER_ID>/disable" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"reason":"Policy violation"}'

curl -X POST "http://localhost:8080/couriers/<USER_ID>/enable" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

After approval, disabling, or re-enabling a courier, log in again to obtain a JWT containing the updated roles. Delivery-service provisioning is asynchronous.

### 3. Browse restaurants and menu (catalog-service)
```bash
# Public catalog endpoints; no token is required
curl http://localhost:8080/restaurants

RESTAURANT_ID="550e8400-e29b-41d4-a716-446655440001"  # Pizza Palace
curl http://localhost:8080/restaurants/$RESTAURANT_ID/menu
```

Restaurant owners and administrators can organize menu items into ordered sections. Sections are managed with `POST`, `PUT`, and `DELETE` under `/restaurants/{restaurantId}/menu/sections`, while customers can retrieve them with `GET`. A menu item may reference a section through `sectionId`; sections cannot be deleted while menu items still reference them.

```bash
curl -X POST "http://localhost:8080/restaurants/$RESTAURANT_ID/menu/sections" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Pizzas","displayOrder":1}'

SECTION_ID="<section id from above>"

curl "http://localhost:8080/restaurants/$RESTAURANT_ID/menu/sections"

curl -X POST "http://localhost:8080/restaurants/$RESTAURANT_ID/menu/items" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"sectionId\":\"$SECTION_ID\",\"name\":\"Margherita Pizza\",\"description\":\"Fresh mozzarella, tomato sauce, basil\",\"price\":12.99,\"available\":true,\"version\":0}"

curl -X PUT "http://localhost:8080/restaurants/$RESTAURANT_ID/menu/sections/$SECTION_ID" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Signature Pizzas","displayOrder":1}'

# Deletion succeeds only after the section has no menu items referencing it.
EMPTY_SECTION_ID="<id of an empty section>"
curl -X DELETE "http://localhost:8080/restaurants/$RESTAURANT_ID/menu/sections/$EMPTY_SECTION_ID" \
  -H "Authorization: Bearer $OWNER_TOKEN"
```

### 4. Add items to cart and checkout (cart-service)
```bash
MENU_ITEM_ID="550e8400-e29b-41d4-a716-446655440101"  # Margherita Pizza

# Add item to cart
curl -X POST http://localhost:8080/cart/items \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"restaurantId\":\"$RESTAURANT_ID\",\"menuItemId\":\"$MENU_ITEM_ID\",\"quantity\":2}"

curl http://localhost:8080/cart \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

# Checkout returns 202 while CartCheckedOutV1 is processed asynchronously.
# Use the cartId returned above as the idempotency key.
CART_ID="<cart_uuid>"
curl -X POST http://localhost:8080/cart/checkout \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: $CART_ID"
# => {"cartId":"<cart_uuid>"}
# Retrying with the same cartId returns the same checkout response.
```

### 5. Track the order (order-service)
```bash
# Get your orders
curl http://localhost:8080/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

# Resolve the asynchronously-created order using the checkout cart ID
curl http://localhost:8080/orders/by-cart/$CART_ID \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"

ORDER_ID="<uuid from response>"
curl http://localhost:8080/orders/$ORDER_ID \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

The order flows automatically through the saga:
```
CartCheckedOut → OrderCreated + PaymentRequested
→ PaymentAuthorized → RestaurantAcceptanceRequested
→ RestaurantAccepted → DeliveryRequested → CourierOffer
→ CourierAccepts → CourierAssigned
→ RestaurantOrderReady → OrderReadyForPickup
→ CourierPickup → OrderPickedUp → CourierDeliver → OrderDelivered
```

### 6. Process the order in the kitchen (restaurant-ops-service)
```bash
curl -X PATCH http://localhost:8080/ops/orders/$ORDER_ID/status \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"ACCEPTED","etaMinutes":20}'

curl -X PATCH http://localhost:8080/ops/orders/$ORDER_ID/status \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}'

curl -X PATCH http://localhost:8080/ops/orders/$ORDER_ID/status \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"READY"}'
```

### 7. Accept, pick up, and deliver (delivery-service)

When a `DeliveryRequestedV1` event arrives, delivery-service creates one delivery and offers it to an available courier. The courier must accept before pickup. Rejected or expired offers remain in assignment history and dispatch continues with another courier.

```bash
curl "http://localhost:8080/assignments?active=true" \
  -H "Authorization: Bearer $COURIER_TOKEN"

curl "http://localhost:8080/assignments?orderId=$ORDER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

ASSIGNMENT_ID="<uuid from the offered assignment>"

curl -X POST http://localhost:8080/assignments/$ASSIGNMENT_ID/accept \
  -H "Authorization: Bearer $COURIER_TOKEN"

curl -X POST http://localhost:8080/assignments/$ASSIGNMENT_ID/pickup \
  -H "Authorization: Bearer $COURIER_TOKEN"

curl -X POST http://localhost:8080/assignments/$ASSIGNMENT_ID/deliver \
  -H "Authorization: Bearer $COURIER_TOKEN"
```

### 8. Alternatively, cancel before delivery completes

Run this instead of completing the delivery workflow above; delivered orders cannot be cancelled.

```bash
curl -X PATCH "http://localhost:8080/orders/$ORDER_ID/cancellation?reason=Changed%20my%20mind" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```
Cancellation policy:
- `PENDING` / `PAYMENT_AUTHORIZED` → full refund and immediate cancellation
- `RESTAURANT_ACCEPTED` → no refund and a fee of `5.00`
- `READY_FOR_PICKUP` / `OUT_FOR_DELIVERY` → no refund and a fee of `8.00`

Late cancellations enter `CANCELLING` until payment-service confirms that the fee was charged. If fee charging fails, the order remains in `CANCELLING`.

---

## API errors

HTTP errors use RFC 9457 `application/problem+json` responses. The `code` property is stable for clients, while `detail` contains the human-readable explanation.

Example:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "status: must not be null",
  "code": "VALIDATION_ERROR"
}
```

## System tests

The infrastructure-backed workflows live in the dedicated `tests/system-tests` module and are opt-in so the normal Maven test suite does not require Docker or running services.

Build the JARs first:

```bash
mvn -DskipTests -q package
```

Then, from PowerShell at the repository root, with Docker running:

```powershell
$env:RUN_INTEGRATION_TESTS="true"
mvn -pl tests/system-tests -am -Dtest=FoodDeliveryIntegrationTests test
```

The system-test module uses Testcontainers to start and clean up the existing
Docker Compose stack automatically. Docker Compose remains available for local
development and manual service inspection.

The 9 scenarios cover successful checkout and delivery, customer courier application and approval, payment failure, restaurant rejection, cancellation after payment authorization, late cancellation with fee charging, rejected-offer reassignment history, expired-offer reassignment, and checkout idempotency with customer order isolation.

## Observability

Every HTTP service exposes Actuator health, info, and metrics endpoints. Stateful services publish
`food_delivery_outbox_events` and `food_delivery_dlt_events` gauges by status, which can be used to alert on
backlog growth or parked events. The API gateway generates or forwards an `X-Request-ID` header, and downstream
services include it in their `rid` log field.

For local inspection, query a service directly, for example:

```text
http://localhost:8083/actuator/metrics/food_delivery_outbox_events
http://localhost:8083/actuator/metrics/food_delivery_dlt_events
```

In production, expose management endpoints on a restricted management port and connect these metrics to the
monitoring system. Alert on increasing `PENDING`/`IN_FLIGHT` outbox events and any `PARKED` DLT events.

## Swagger UI

The API gateway provides a unified Swagger UI with all client-facing service specifications:

- UI: `http://localhost:8080/swagger-ui.html`
- The gateway proxies `/openapi/auth`, `/openapi/catalog`, `/openapi/cart`, `/openapi/order`, `/openapi/ops`, and `/openapi/delivery` so the browser does not need direct cross-origin access to those backend services.

Each HTTP service also exposes its own Swagger UI at `/swagger-ui.html` and OpenAPI document at `/v3/api-docs`:

| Service | URL |
|---|---|
| auth-service | http://localhost:8082/swagger-ui.html |
| order-service | http://localhost:8083/swagger-ui.html |
| payment-service | http://localhost:8084/swagger-ui.html |
| catalog-service | http://localhost:8085/swagger-ui.html |
| cart-service | http://localhost:8086/swagger-ui.html |
| restaurant-ops-service | http://localhost:8087/swagger-ui.html |
| delivery-service | http://localhost:8088/swagger-ui.html |
| notification-service | http://localhost:8089/swagger-ui.html |
| user-service | http://localhost:8090/swagger-ui.html |

---

## Seed Data

Liquibase seeds:

- Roles: `CUSTOMER`, `RESTAURANT_OWNER`, `COURIER`, `ADMIN`
- Users `customer1`, `owner1`, `owner2`, `courier1`, `courier2`, and `admin`, all with password `admin123`
- Courier records for `courier1` and `courier2`
- 2 restaurants: **Pizza Palace** (`550e8400-...-446655440001`) and **Burger Barn** (`550e8400-...-446655440002`)
- 4 menu items (2 per restaurant)

---

## Future Work

- Expand notification-service beyond lifecycle logging. Add notification templates and user preferences, support email/SMS/push/in-app channels, persist notification history and delivery status, make handling idempotent using source event IDs, and add retry/dead-letter handling for failed deliveries.

- Add JaCoCo coverage reporting to Maven and CI. Publish coverage reports as workflow artifacts first, then introduce minimum thresholds after the baseline is understood and the remaining uncovered core-service paths are addressed.

- Replace the current re-login requirement after role changes with short-lived access tokens and refresh tokens. Refresh-token rotation and revocation should ensure that newly approved, disabled, or re-enabled courier roles become effective without requiring the customer to log in again.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Build fails — parent not found | Run `mvn clean install` from the **repo root** |
| Kafka/Schema Registry errors | Ensure `docker compose up -d` is running and ports 9094/8081 are free |
| DB connection refused | Check `docker compose ps` — Postgres must be healthy |
| 401 Unauthorized | Token expired or missing `Authorization: Bearer <token>` header |
| 403 Forbidden | Use an account with the required role. After courier approval or an admin role change, obtain a fresh JWT; courier profile provisioning is asynchronous. |
| Cart menu item not found | cart-service loads menu cache from catalog-service on startup — ensure catalog-service is running first |
