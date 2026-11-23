# Pizza Order Service

A pizza order builder (web UI) plus an order service (REST API). Customers build an order, see the total update live, submit it, check its status, and update/cancel it until the kitchen starts it.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, Vanilla JavaScript (served by Spring Boot as static files) |
| Backend | Java 21, Spring Boot 3.5, REST API |
| Database | SQLite (Spring Data JPA + Hibernate community dialect) |
| Build | Gradle 9 (Kotlin DSL, wrapper included) |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Spring Boot Test, MockMvc |

## How to Run

### With Docker (recommended)

```bash
docker compose up --build
```

Open http://localhost:8080. The SQLite file is persisted in the `pizza-data` volume.

### Locally

Requires JDK 21+.

```bash
./gradlew bootRun
```

Open http://localhost:8080. The database is created at `data/pizza-orders.db` and the menu is seeded on first start.

### Run Tests

```bash
./gradlew test
```

> The order builder (browser UI) and the order service run as separate processes communicating over HTTP: the UI runs in the browser and calls the service's REST API with `fetch`.

## API

Base URL: `http://localhost:8080`

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/menu` | List sizes and toppings |
| `POST` | `/api/orders/calculate` | Price an order without saving it (live total) |
| `POST` | `/api/orders` | Submit an order → returns order with `id` (idempotent via `idempotencyKey`) |
| `GET` | `/api/orders/{id}` | Order details + pricing breakdown |
| `GET` | `/api/orders/{id}/status` | Order status |
| `PUT` | `/api/orders/{id}` | Replace the order contents (only while `NOT_STARTED`) |
| `DELETE` | `/api/orders/{id}` | Cancel (only while `NOT_STARTED`) |
| `PATCH` | `/api/admin/orders/{id}/advance` | **Restaurant side:** advance status one step |

### Examples

```bash
# Menu
curl http://localhost:8080/api/menu

# Submit an order (2 large pizzas with toppings 1 and 7, promo, 15% tip)
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
        "idempotencyKey": "6f1c2a1e-1111-4c1b-9d5e-000000000001",
        "lineItems": [{"sizeId": 3, "quantity": 2, "toppingIds": [1, 7]}],
        "promoCode": "ILOVEPIZZA",
        "tipMode": "PERCENTAGE",
        "tipValue": 15
      }'

# Check status
curl http://localhost:8080/api/orders/1/status

# Cancel
curl -X DELETE http://localhost:8080/api/orders/1

# Restaurant: advance NOT_STARTED → STARTED → READY_FOR_PICKUP → DELIVERED
curl -X PATCH http://localhost:8080/api/admin/orders/1/advance
```

Errors are returned as JSON: `{"status":409,"error":"Conflict","message":"Order 1 cannot be modified (status: STARTED)", "timestamp": "..."}`.
Status codes: `400` invalid input / promo, `404` unknown order, `409` illegal state transition or concurrent modification.

### Simulating the restaurant

The kitchen is simulated with the `PATCH /api/admin/orders/{id}/advance` endpoint (curl above). It moves an order exactly one step forward. An explicit endpoint, rather than a timer, keeps state transitions deterministic and testable.

## Assumptions

- **Rounding:** `BigDecimal` with `HALF_UP` to 2 decimals. Each topping price is rounded per pizza, then line totals, discount, tax and tip are each rounded. The total is the sum of the rounded parts, so the displayed breakdown always adds up.
- **Toppings** are charged per pizza: a line with quantity 3 pays for its toppings 3 times. A topping can appear at most once per pizza (no "double pepperoni").
- **Tip percentage** is calculated on the discounted subtotal + tax (the "total bill").
- **Tax** is applied after the promo discount.
- **SAVE5** needs a subtotal *strictly* over \$20, measured before any discount.
- **One promo code per order.** That makes SAVE5 + ILOVEPIZZA impossible by construction; each promo also declares its conflicts (`conflictsWith()`) so stacking can be supported later.
- **Promo codes** are case-insensitive. An unknown or inapplicable code is **rejected with a 400** when the order is submitted or updated. In the live preview (`/calculate`) it is just not applied.
- **Negative tips** are treated as zero by the pricing engine (they should be rejected with a 400; see Known gaps). Empty orders cannot be submitted (at least one line item required).
- **Cancel** is a status (`CANCELLED`), not a delete, so a customer can still check a cancelled order.
- **Idempotency:** the client generates a UUID `idempotencyKey` per submission. A retry with the same key returns the original order instead of creating a duplicate. A different payload with the same key also returns the original order (no conflict detection).

## Design Write-Up

**Money and totals.** All money is `BigDecimal`, never floating point. `PricingService` is the **single source of truth** and runs only on the server. The UI never does arithmetic. For the live total it calls `POST /api/orders/calculate`, which runs the same code as order submission. What the customer sees is exactly what the restaurant charges. The total is never stored; it's recalculated from the order's items, so it can't drift out of sync.

**Extensibility.**
- *Sizes, toppings, topping categories* are database rows (`pizza_sizes`, `toppings`, `topping_categories`). A category holds its percentage, and the pricing engine uses `basePrice × category.pricePercentage`. Adding "Large XL" or a "Gourmet 15%" category is a data change only (`DataInitializer` seeds the defaults).
- *Promo codes* use a strategy pattern: `PromoCodeStrategy` has `isApplicable(subtotal)`, `calculateDiscount(subtotal)` and `conflictsWith()`. A new promo with its own conditions is one new class registered in `PromoCodeRegistry`. `PricingService` doesn't change.

**Order lifecycle.** `OrderStatus` is an explicit state machine: `next()` only allows forward steps, and `isModifiable()` is true only for `NOT_STARTED`. `CANCELLED` is terminal.
- *Retry after timeout:* the browser creates one `idempotencyKey` per submit. If the response is lost and the client retries, the service finds the existing order by that key (unique column) and returns it. Result: exactly one order.
- *Cancel vs. kitchen start race:* `PizzaOrder` has a JPA `@Version` column (optimistic locking). Cancel and advance each read, check the state, and write in a transaction. If they overlap, the second commit fails its version check and gets **409 Conflict**. If they don't overlap, the second request sees the new state and is rejected (cancel after STARTED → 409; advance after CANCELLED → 409). Guarantee: an order is never both started and cancelled.

**What I cut / would do next.** See *Known gaps* below.

## Test Coverage

Run with `./gradlew test`.

- `PricingServiceTest` (unit): base prices, basic/premium topping percentages, quantity scaling, multi-line orders, both promos incl. the SAVE5 threshold, invalid and case-insensitive codes, dollar and percentage tips, every amount has exactly 2 decimals, and **total == taxable + tax + tip**. Pricing is the highest-weighted area, so it has the densest tests.
- `OrderControllerIntegrationTest` (API): create/get round-trip, **idempotent retry returns the same order**, full lifecycle including advancing past DELIVERED (409), cancel allowed only while NOT_STARTED, update rejected after STARTED, invalid promo → 400, menu, pricing preview.

**Not covered yet:** a true concurrent cancel/advance test (two threads), idempotency key reused with a different payload, and frontend tests.

## Known gaps / next steps

- Idempotency doesn't detect a reused key with a different payload (should return 409/422).
- Concurrency is protected by optimistic locking but not stress-tested. SQLite with a single connection also serializes writes.
- No auth on the admin endpoint (out of scope by requirements).
- The UI submits a new order instead of editing a submitted one (the API supports `PUT`).

## AI Usage Note

*To be completed by the author:* which tools were used and for what, one AI suggestion that was rejected or fixed, and one invariant verified manually.
