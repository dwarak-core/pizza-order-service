package com.pizza.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of a pricing calculation with full breakdown.
 */
public record PricingResult(
    BigDecimal subtotal,
    BigDecimal discount,
    String appliedPromoCode,
    BigDecimal taxableAmount,
    BigDecimal tax,
    BigDecimal tip,
    BigDecimal total,
    List<LineItemPricing> lineItems
) {
    public record LineItemPricing(
        String size,
        int quantity,
        List<String> toppings,
        BigDecimal unitPrice,
        BigDecimal toppingsPrice,
        BigDecimal lineTotal
    ) {}
}
