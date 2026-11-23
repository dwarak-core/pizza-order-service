package com.pizza.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    String status,
    List<LineItemResponse> lineItems,
    PricingResponse pricing,
    String promoCode,
    String tipMode,
    BigDecimal tipValue,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
