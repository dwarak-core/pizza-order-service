package com.pizza.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record LineItemRequest(
    @NotNull Long sizeId,
    @Min(1) int quantity,
    Set<Long> toppingIds
) {}
