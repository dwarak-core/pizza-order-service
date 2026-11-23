# Pizza Order Service — Development Plan

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, Vanilla JavaScript |
| Backend | Java 21, Spring Boot 3.x, REST API |
| Database | SQLite (via Spring Data JPA + Hibernate) |
| Build | Gradle (Kotlin DSL) |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Spring Boot Test, MockMvc |

---

## Phase 1: Project Scaffolding & Data Model

> Foundation — get the project structure, database schema, and domain objects right before writing any logic.

### Step 1.1 — Initialize Spring Boot Project

- Create a Spring Boot 3.x project with Gradle (Kotlin DSL) and dependencies:
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `sqlite-jdbc` driver
  - `hibernate-community-dialects` (SQLite dialect)
- Configure `application.yml` with SQLite datasource pointing to a local file (`pizza-orders.db`)
- Verify the app starts and connects to SQLite

### Step 1.2 — Define Domain Entities

Create JPA entities for the core data model:

| Entity | Key Fields |
|---|---|
| `PizzaSize` | id, name, basePrice (BigDecimal) |
| `ToppingCategory` | id, name, pricePercentage (BigDecimal) |
| `Topping` | id, name, category (FK → ToppingCategory) |
| `Order` | id, status (enum), promoCode, tipMode, tipValue, createdAt, updatedAt, idempotencyKey, version |
| `LineItem` | id, order (FK → Order), size (FK → PizzaSize), quantity |
| `LineItemTopping` | id, lineItem (FK → LineItem), topping (FK → Topping) |

Key design decisions:
- **All prices stored as `BigDecimal`** — never `double` or `float`
- **`PizzaSize` and `ToppingCategory` are database tables**, not hardcoded enums — this supports Requirement 6 (extensibility)
- **`OrderStatus` is a Java enum**: `NOT_STARTED`, `STARTED`, `READY_FOR_PICKUP`, `DELIVERED`
- **`idempotencyKey`** (UUID, unique) on `Order` to handle retry scenarios
- **`version`** field with `@Version` for optimistic locking

### Step 1.3 — Seed Menu Data

- Create `data.sql` (or a `DataInitializer` component) to seed:
  - 3 sizes: Small ($5), Medium ($7), Large ($12)
  - 2 topping categories: Basic (8%), Premium (11%)
  - 12 toppings mapped to their categories
- Verify seed data loads correctly on startup

### Step 1.4 — Git Commit

```bash
git add -A && git commit -m "Phase 1: Project scaffolding and data model"
```

---

## Phase 2: Pricing Engine

> The heart of the system. Build and test this in isolation before wiring it to any API.

### Step 2.1 — Implement Pricing Service

Create a `PricingService` class that computes the order total:

1. **Line item subtotal** = `basePrice × quantity` + topping costs per pizza × quantity
   - Basic topping cost = `basePrice × 0.08`
   - Premium topping cost = `basePrice × 0.11`
2. **Order subtotal** = sum of all line item subtotals
3. **Apply promo code** (before tax):
   - `ILOVEPIZZA`: 10% off order subtotal
   - `SAVE5`: $5 off if subtotal > $20
   - Mutual exclusion: `SAVE5` cannot combine with `ILOVEPIZZA`
4. **Tax** = 6.5% of discounted subtotal
5. **Tip**:
   - Dollar mode: flat amount
   - Percentage mode: percentage of (discounted subtotal + tax)
6. **Grand total** = discounted subtotal + tax + tip

> **Rounding rule:** Use `BigDecimal` with `RoundingMode.HALF_UP` and scale of 2 at each displayed amount. Document the rounding approach in the README as an assumption.

### Step 2.2 — Pricing Unit Tests

Write JUnit tests for the pricing invariants the evaluators care most about:

| Test Case | What It Verifies |
|---|---|
| Single small pizza, no toppings | Base price calculation |
| Pizza with mixed basic + premium toppings | Percentage-of-base topping pricing |
| Multi-line-item order | Summation across line items |
| `ILOVEPIZZA` promo | 10% discount applied before tax |
| `SAVE5` promo — over $20 | $5 discount applied |
| `SAVE5` promo — under $20 | Discount rejected |
| Both promos attempted | Mutual exclusion enforced |
| Tip as dollar amount | Added after tax |
| Tip as percentage | Computed on (subtotal + tax) |
| Rounding edge cases | Amounts are whole cents |
| Empty order | Zero total, no errors |

### Step 2.3 — Git Commit

```bash
git add -A && git commit -m "Phase 2: Pricing engine with unit tests"
```

---

## Phase 3: Order REST API & Lifecycle

> Wire the domain model and pricing into a REST API with proper validation and concurrency handling.

### Step 3.1 — Order Builder Endpoints (Customer-Facing)

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/orders` | Create a new order (accepts `idempotencyKey`) |
| `GET` | `/api/orders/{id}` | Get order details + computed total |
| `PUT` | `/api/orders/{id}` | Update the full order (line items, toppings, tip, promo) |
| `DELETE` | `/api/orders/{id}` | Cancel the order |
| `POST` | `/api/orders/{id}/submit` | Submit the order to the restaurant (locks it in) |
| `GET` | `/api/orders/{id}/status` | Query order status |

Validations:
- Cannot update/cancel an order unless status is `NOT_STARTED`
- Promo code validation at submission time
- Reject unknown sizes, toppings
- Return clear error messages (400 with a JSON error body)

### Step 3.2 — Restaurant-Side Endpoint (Status Advancement)

| Method | Endpoint | Purpose |
|---|---|---|
| `PATCH` | `/api/admin/orders/{id}/advance` | Advance order to the next status |

State transitions enforced:
```
NOT_STARTED → STARTED → READY_FOR_PICKUP → DELIVERED
```
- Only forward transitions allowed, one step at a time
- Attempting to advance a `DELIVERED` order returns an error
- Advancing from `NOT_STARTED` → `STARTED` locks out customer updates/cancellations

### Step 3.3 — Handle Concurrency & Retries

- **Idempotent order creation:** If a `POST /api/orders` arrives with an `idempotencyKey` that already exists, return the existing order instead of creating a duplicate
- **Cancel-race protection:** Use optimistic locking (`@Version` on the `Order` entity) to handle the case where a customer cancels at the exact moment the kitchen advances the status. Whoever commits first wins; the other gets a conflict error (409)
- **Retry-safe reads:** `GET` endpoints are naturally idempotent

### Step 3.4 — API Integration Tests

Write MockMvc or `@SpringBootTest` tests for:

| Test Case | What It Verifies |
|---|---|
| Create order → get it back | Round-trip with correct total |
| Submit → advance → query status | Full lifecycle |
| Cancel while `NOT_STARTED` | Cancel succeeds |
| Cancel while `STARTED` | Cancel rejected (409 or 400) |
| Retry with same idempotency key | Returns same order, no duplicate |
| Update after `STARTED` | Update rejected |
| Advance past `DELIVERED` | Error returned |
| Invalid promo code | 400 with message |

### Step 3.5 — Git Commit

```bash
git add -A && git commit -m "Phase 3: REST API, order lifecycle, and integration tests"
```

---

## Phase 4: Frontend (Order Builder UI)

> Minimal but functional. Keep it simple — the evaluators don't grade CSS.

### Step 4.1 — Order Builder Page

Build a single-page app in `src/main/resources/static/`:

- **Add Line Item form:** dropdown for size, checkboxes for toppings, quantity input
- **Order summary panel:** lists all line items with subtotals, shows running total (Req 2)
- **Promo code input** with apply/clear
- **Tip input** with toggle between dollar / percentage mode
- **Action buttons:** Submit Order, Cancel Order, Check Status

### Step 4.2 — Live Total Updates

- On every change (add/remove item, change topping, change tip/promo), call `PUT /api/orders/{id}` and re-render the total from the response
- The server-computed total is **always the source of truth** (the rubric wants "one source of truth for the total")

### Step 4.3 — Order Description (Req 3)

- Add a "View Order Description" button that calls `GET /api/orders/{id}` and displays a formatted text summary:
  ```
  1x Large Pizza — pepperoni, bacon
  2x Small Pizza — extra cheese
  Subtotal: $29.76
  Promo (ILOVEPIZZA): -$2.98
  Tax: $1.74
  Tip: $5.00
  Total: $33.52
  ```

### Step 4.4 — Error Handling

- Display server error messages in a visible alert/banner
- Handle network timeouts gracefully (show "Connection issue, retrying..." rather than a blank failure)

### Step 4.5 — Git Commit

```bash
git add -A && git commit -m "Phase 4: Frontend order builder UI"
```

---

## Phase 5: Docker Setup

> Make it one command to start everything.

### Step 5.1 — Dockerfile (Multi-stage)

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 5.2 — docker-compose.yml

```yaml
services:
  pizza-service:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - pizza-data:/app/data
volumes:
  pizza-data:
```

### Step 5.3 — Verify

- `docker compose up --build` starts everything
- Frontend accessible at `http://localhost:8080`
- API accessible at `http://localhost:8080/api/...`

### Step 5.4 — Git Commit

```bash
git add -A && git commit -m "Phase 5: Docker and Docker Compose setup"
```

---

## Phase 6: Documentation & Polish

> The README is a deliverable — the evaluators read it closely.

### Step 6.1 — Finalize README.md

Ensure all required sections are complete:

1. **How to run** — `docker compose up` and `./gradlew bootRun`
2. **How to run tests** — `./gradlew test`
3. **API description** — table of endpoints with curl examples
4. **Assumptions** — document every ambiguity decision:
   - Rounding strategy (HALF_UP, applied per displayed amount)
   - Tip percentage base (subtotal + tax? or just subtotal?)
   - Whether toppings cost is per-pizza or per-line-item
   - Promo code case sensitivity
   - What happens if you apply a promo then remove items so the order drops below $20
5. **Design write-up** — answer the four questions from the requirements
6. **AI usage note**
7. **Test coverage** — which invariants are tested and why

### Step 6.2 — Code Cleanup

- Remove dead code, unused imports
- Add Javadoc to public service methods
- Consistent error response format across all endpoints

### Step 6.3 — Git Commit

```bash
git add -A && git commit -m "Phase 6: Documentation, README, and code cleanup"
```

---

## Priority Order (If Time-Constrained)

If running low on time, deliver in this order — each phase is independently valuable:

| Priority | Phase | Why |
|---|---|---|
| 🔴 P0 | Phase 1 + 2 (Model + Pricing + Tests) | The rubric weights correct pricing highest |
| 🔴 P0 | Phase 3 (REST API + Lifecycle) | Service design and state machine are core evaluation criteria |
| 🟡 P1 | Phase 6 (README / Docs) | Evaluators read this closely; write-up answers are scored |
| 🟡 P1 | Phase 4 (Frontend) | Needs to be functional, not polished |
| 🟢 P2 | Phase 5 (Docker) | Nice-to-have; a plus, not required |

> **Don't skip the README to build more features.** An honest, well-written README with a cut list scores better than a half-broken feature.

---

## README Update Schedule

The README.md is created at project start and updated at each phase:

| After Phase | README Additions |
|---|---|
| Phase 1 | Project description, tech stack, how to run (basic) |
| Phase 2 | Assumptions section (rounding, pricing decisions) |
| Phase 3 | API documentation with curl examples, order lifecycle description |
| Phase 4 | Frontend usage instructions |
| Phase 5 | Docker instructions added to "How to run" |
| Phase 6 | Design write-up, AI usage note, test coverage summary |

