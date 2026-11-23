package com.pizza.dto;

import java.math.BigDecimal;
import java.util.List;

public record MenuResponse(
    List<SizeOption> sizes,
    List<ToppingOption> toppings
) {
    public record SizeOption(Long id, String name, BigDecimal basePrice) {}
    public record ToppingOption(Long id, String name, String category, BigDecimal pricePercentage) {}
}
