package com.pizza.model;

/**
 * Represents the lifecycle states of a pizza order.
 * Transitions are strictly forward: NOT_STARTED → STARTED → READY_FOR_PICKUP → DELIVERED.
 * CANCELLED is a terminal state reachable only from NOT_STARTED.
 */
public enum OrderStatus {
    NOT_STARTED,
    STARTED,
    READY_FOR_PICKUP,
    DELIVERED,
    CANCELLED;

    /**
     * Returns the next status in the kitchen workflow.
     * @throws IllegalStateException if the order is already delivered or cancelled
     */
    public OrderStatus next() {
        return switch (this) {
            case NOT_STARTED -> STARTED;
            case STARTED -> READY_FOR_PICKUP;
            case READY_FOR_PICKUP -> DELIVERED;
            case DELIVERED -> throw new IllegalStateException("Order is already delivered");
            case CANCELLED -> throw new IllegalStateException("Order is cancelled");
        };
    }

    /**
     * Returns true if the order can still be modified or cancelled by the customer.
     */
    public boolean isModifiable() {
        return this == NOT_STARTED;
    }
}

