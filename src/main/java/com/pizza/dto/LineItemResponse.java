package com.pizza.dto;

import java.math.BigDecimal;
import java.util.List;

public record LineItemResponse(
    Long id,
    Long sizeId,
    String sizeName,
    int quantity,
    List<ToppingInfo> toppings,
    BigDecimal lineTotal
) {
    public record ToppingInfo(Long id, String name) {}
}
