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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
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
                .andExpect(jsonPath("$.total").isNumber());
    }

    private void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
