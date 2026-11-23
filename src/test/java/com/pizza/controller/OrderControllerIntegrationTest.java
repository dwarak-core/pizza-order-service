package com.pizza.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pizza.dto.CreateOrderRequest;
import com.pizza.dto.LineItemRequest;
import com.pizza.dto.UpdateOrderRequest;
import com.pizza.repository.PizzaSizeRepository;
import com.pizza.repository.ToppingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PizzaSizeRepository sizeRepository;

    @Autowired
    private ToppingRepository toppingRepository;

    private Long getLargeId() {
        return sizeRepository.findByNameIgnoreCase("Large").orElseThrow().getId();
    }

    private Long getSmallId() {
        return sizeRepository.findByNameIgnoreCase("Small").orElseThrow().getId();
    }

    private Long getPepperoniId() {
        return toppingRepository.findByNameIgnoreCase("Pepperoni").orElseThrow().getId();
    }

    private Long getBaconId() {
        return toppingRepository.findByNameIgnoreCase("Bacon").orElseThrow().getId();
    }

    private CreateOrderRequest simpleOrder() {
        return new CreateOrderRequest(
                UUID.randomUUID().toString(),
                List.of(new LineItemRequest(getLargeId(), 1, Set.of())),
                null, null, null
        );
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // --- Create & Get ---

    @Test
    @DisplayName("Create order and get it back with correct total")
    void createAndGetOrder() throws Exception {
        CreateOrderRequest req = simpleOrder();

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.pricing.subtotal").value(12.00))
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.pricing.subtotal").value(12.00));
    }

    // --- Idempotency ---

    @Test
    @DisplayName("Retry with same idempotency key returns same order")
    void idempotentCreate() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest(
                "test-idemp-key-" + UUID.randomUUID(),
                List.of(new LineItemRequest(getLargeId(), 1, Set.of())),
                null, null, null
        );

        MvcResult first = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("id").asLong();

        // Retry with same idempotency key
        MvcResult second = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long secondId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("id").asLong();

        assertEquals(firstId, secondId);
    }

    // --- Full lifecycle ---

    @Test
    @DisplayName("Full lifecycle: create → advance × 3 → delivered")
    void fullLifecycle() throws Exception {
        CreateOrderRequest req = simpleOrder();

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Advance: NOT_STARTED → STARTED
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));

        // Advance: STARTED → READY_FOR_PICKUP
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"));

        // Advance: READY_FOR_PICKUP → DELIVERED
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Cannot advance past DELIVERED
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                .andExpect(status().isConflict());
    }

    // --- Cancel ---

    @Test
    @DisplayName("Cancel while NOT_STARTED succeeds")
    void cancelWhileNotStarted() throws Exception {
        CreateOrderRequest req = simpleOrder();

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isNoContent());

        // Verify cancelled
        mockMvc.perform(get("/api/orders/" + orderId + "/status"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Cancel after STARTED is rejected")
    void cancelAfterStarted() throws Exception {
        CreateOrderRequest req = simpleOrder();

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Advance to STARTED
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                .andExpect(status().isOk());

        // Try to cancel — should fail
        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isConflict());
    }

    // --- Update ---

    @Test
    @DisplayName("Update order while NOT_STARTED succeeds")
    void updateWhileNotStarted() throws Exception {
        CreateOrderRequest req = simpleOrder();

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        UpdateOrderRequest updateReq = new UpdateOrderRequest(
                List.of(new LineItemRequest(getSmallId(), 2, Set.of(getPepperoniId()))),
                null, null, null
        );

        mockMvc.perform(put("/api/orders/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[0].sizeName").value("Small"))
                .andExpect(jsonPath("$.lineItems[0].quantity").value(2));
    }

    @Test
    @DisplayName("Update after STARTED is rejected")
    void updateAfterStarted() throws Exception {
        CreateOrderRequest req = simpleOrder();

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                .andExpect(status().isOk());

        UpdateOrderRequest updateReq = new UpdateOrderRequest(
                List.of(new LineItemRequest(getSmallId(), 1, Set.of())),
                null, null, null
        );

        mockMvc.perform(put("/api/orders/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateReq)))
                .andExpect(status().isConflict());
    }

    // --- Promo codes ---

    @Test
    @DisplayName("Invalid promo code returns 400")
    void invalidPromoCode() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest(
                UUID.randomUUID().toString(),
                List.of(new LineItemRequest(getLargeId(), 1, Set.of())),
                "FAKECODE", null, null
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isBadRequest());
    }

    // --- Menu ---

    @Test
    @DisplayName("Menu endpoint returns sizes and toppings")
    void getMenu() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizes", hasSize(3)))
                .andExpect(jsonPath("$.toppings", hasSize(12)));
    }

    // --- Pricing calculate ---

    @Test
    @DisplayName("Calculate pricing without creating order")
    void calculatePricing() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest(
                null,
                List.of(new LineItemRequest(getLargeId(), 2, Set.of(getPepperoniId(), getBaconId()))),
                "ILOVEPIZZA", null, null
        );

        mockMvc.perform(post("/api/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").isNumber())
                .andExpect(jsonPath("$.discount").isNumber())
                .andExpect(jsonPath("$.appliedPromoCode").value("ILOVEPIZZA"))
                .andExpect(jsonPath("$.promoError").doesNotExist())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @DisplayName("Calculate pricing returns promoError for invalid promo code")
    void calculatePricingInvalidPromo() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest(
                null,
                List.of(new LineItemRequest(getLargeId(), 1, Set.of())),
                "BADCODE", null, null
        );

        mockMvc.perform(post("/api/orders/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode").doesNotExist())
                .andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.promoError").value("Invalid promo code: BADCODE"));
    }

    @Test
    @DisplayName("Concurrent cancel and advance: exactly one wins, other gets 409")
    void concurrentCancelAndAdvance() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(simpleOrder())))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> cancelStatus = pool.submit(() -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(delete("/api/orders/" + orderId))
                    .andReturn().getResponse().getStatus();
        });

        Future<Integer> advanceStatus = pool.submit(() -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                    .andReturn().getResponse().getStatus();
        });

        ready.await();
        start.countDown();

        int s1 = cancelStatus.get();
        int s2 = advanceStatus.get();
        pool.shutdown();

        boolean cancelWon = (s1 == 204 && s2 == 409);
        boolean advanceWon = (s1 == 409 && s2 == 200);
        assertTrue(cancelWon || advanceWon,
                "Expected one winner and one 409, got cancel=" + s1 + ", advance=" + s2);

        String expectedFinal = cancelWon ? "CANCELLED" : "STARTED";
        mockMvc.perform(get("/api/orders/" + orderId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedFinal));
    }

    @Test
    @DisplayName("Reusing idempotency key with different payload returns 409 Conflict")
    void idempotencyKeyDifferentPayloadConflict() throws Exception {
        String key = "conflict-key-" + UUID.randomUUID();

        CreateOrderRequest firstReq = new CreateOrderRequest(
                key,
                List.of(new LineItemRequest(getLargeId(), 1, Set.of())),
                null, null, null
        );
        CreateOrderRequest differentReq = new CreateOrderRequest(
                key,
                List.of(new LineItemRequest(getSmallId(), 2, Set.of(getPepperoniId()))),
                null, null, null
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(firstReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(differentReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Concurrent duplicate idempotent creates all return the same order ID")
    void concurrentDuplicateIdempotentCreates() throws Exception {
        String key = "concurrent-create-" + UUID.randomUUID();
        CreateOrderRequest req = new CreateOrderRequest(
                key,
                List.of(new LineItemRequest(getLargeId(), 1, Set.of(getPepperoniId()))),
                null, null, null
        );

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                MvcResult res = mockMvc.perform(post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(req)))
                        .andExpect(status().isCreated())
                        .andReturn();
                return objectMapper.readTree(res.getResponse().getContentAsString())
                        .get("id").asLong();
            }));
        }

        ready.await();
        start.countDown();

        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        pool.shutdown();

        assertEquals(1, ids.size());
    }

    @Test
    @DisplayName("Stress test: 20 concurrent cancel vs advance races maintain state invariant")
    void stressTestConcurrentCancelAndAdvance() throws Exception {
        int iterations = 20;
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (int i = 0; i < iterations; i++) {
            MvcResult created = mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(simpleOrder())))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long orderId = objectMapper.readTree(created.getResponse().getContentAsString())
                    .get("id").asLong();

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Integer> cancelStatus = pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(delete("/api/orders/" + orderId))
                        .andReturn().getResponse().getStatus();
            });

            Future<Integer> advanceStatus = pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/admin/orders/" + orderId + "/advance"))
                        .andReturn().getResponse().getStatus();
            });

            ready.await();
            start.countDown();

            int s1 = cancelStatus.get();
            int s2 = advanceStatus.get();
            boolean cancelWon = (s1 == 204 && s2 == 409);
            boolean advanceWon = (s1 == 409 && s2 == 200);
            assertTrue(cancelWon || advanceWon,
                    "Iteration " + i + ": expected one winner and one 409, got cancel=" + s1 + ", advance=" + s2);
        }
        pool.shutdown();
    }

    private void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
