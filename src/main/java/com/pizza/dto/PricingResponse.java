package com.pizza.dto;

import java.math.BigDecimal;

public record PricingResponse(
    BigDecimal subtotal,
    BigDecimal discount,
    String appliedPromoCode,
    BigDecimal taxableAmount,
    BigDecimal tax,
    BigDecimal tip,
    BigDecimal total,
    String promoError
) {}
