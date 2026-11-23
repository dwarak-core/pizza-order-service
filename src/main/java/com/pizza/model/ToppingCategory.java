package com.pizza.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "topping_categories")
public class ToppingCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Price as a fraction of the base pizza price.
     * e.g., 0.08 means 8% of the base price.
     */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal pricePercentage;

    protected ToppingCategory() {}

    public ToppingCategory(String name, BigDecimal pricePercentage) {
        this.name = name;
        this.pricePercentage = pricePercentage;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPricePercentage() { return pricePercentage; }

    public void setName(String name) { this.name = name; }
    public void setPricePercentage(BigDecimal pricePercentage) { this.pricePercentage = pricePercentage; }
}

