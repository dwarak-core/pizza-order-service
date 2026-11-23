package com.pizza.dto;

import com.pizza.model.TipMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdateOrderRequest(
    @NotNull @NotEmpty List<@Valid LineItemRequest> lineItems,
    String promoCode,
    TipMode tipMode,
    @DecimalMin(value = "0.00", message = "Tip value must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Tip value must have at most 2 decimal places")
    BigDecimal tipValue
) {}
