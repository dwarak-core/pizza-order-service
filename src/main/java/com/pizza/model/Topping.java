package com.pizza.model;

import jakarta.persistence.*;

@Entity
@Table(name = "toppings")
public class Topping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private ToppingCategory category;

    protected Topping() {}

    public Topping(String name, ToppingCategory category) {
        this.name = name;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public ToppingCategory getCategory() { return category; }

    public void setName(String name) { this.name = name; }
    public void setCategory(ToppingCategory category) { this.category = category; }
}

