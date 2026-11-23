package com.pizza.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pizza_sizes")
public class PizzaSize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    protected PizzaSize() {}

    public PizzaSize(String name, BigDecimal basePrice) {
        this.name = name;
        this.basePrice = basePrice;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getBasePrice() { return basePrice; }

    public void setName(String name) { this.name = name; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
}

