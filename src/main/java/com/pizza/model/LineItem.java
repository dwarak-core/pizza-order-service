package com.pizza.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "line_items")
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private PizzaOrder order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "size_id", nullable = false)
    private PizzaSize size;

    @Column(nullable = false)
    private int quantity = 1;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "line_item_toppings",
        joinColumns = @JoinColumn(name = "line_item_id"),
        inverseJoinColumns = @JoinColumn(name = "topping_id")
    )
    private Set<Topping> toppings = new HashSet<>();

    protected LineItem() {}

    public LineItem(PizzaSize size, int quantity) {
        this.size = size;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public PizzaOrder getOrder() { return order; }
    public PizzaSize getSize() { return size; }
    public int getQuantity() { return quantity; }
    public Set<Topping> getToppings() { return toppings; }

    public void setOrder(PizzaOrder order) { this.order = order; }
    public void setSize(PizzaSize size) { this.size = size; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public void addTopping(Topping topping) { this.toppings.add(topping); }
    public void removeTopping(Topping topping) { this.toppings.remove(topping); }
    public void clearToppings() { this.toppings.clear(); }
}

