package com.pizza.controller;

import com.pizza.dto.OrderStatusResponse;
import com.pizza.service.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminController {

    private final OrderService orderService;

    public AdminController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Advance an order to its next status.
     * NOT_STARTED → STARTED → READY_FOR_PICKUP → DELIVERED.
     */
    @PatchMapping("/{id}/advance")
    public OrderStatusResponse advanceOrder(@PathVariable Long id) {
        return orderService.advanceOrderStatus(id);
    }
}
