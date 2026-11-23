package com.pizza.controller;

import com.pizza.dto.*;
import com.pizza.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Create a new order. Idempotent if idempotencyKey is provided. */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Get order details including the full pricing breakdown. */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    /** Update an order. Only allowed while status is NOT_STARTED. */
    @PutMapping("/{id}")
    public OrderResponse updateOrder(@PathVariable Long id,
                                     @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.updateOrder(id, request);
    }

    /** Cancel an order. Only allowed while status is NOT_STARTED. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    /** Query order status. */
    @GetMapping("/{id}/status")
    public OrderStatusResponse getOrderStatus(@PathVariable Long id) {
        return orderService.getOrderStatus(id);
    }

    /** Stateless pricing preview — does not create an order. */
    @PostMapping("/calculate")
    public PricingResponse calculatePricing(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.calculatePricing(request);
    }
}
