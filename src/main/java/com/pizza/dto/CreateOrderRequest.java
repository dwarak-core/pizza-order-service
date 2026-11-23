package com.pizza.dto;

import com.pizza.model.TipMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    String idempotencyKey,
    @NotNull @NotEmpty List<@Valid LineItemRequest> lineItems,
    String promoCode,
    TipMode tipMode,
    BigDecimal tipValue
) {}
